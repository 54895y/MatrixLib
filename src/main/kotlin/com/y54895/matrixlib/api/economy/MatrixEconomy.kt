package com.y54895.matrixlib.api.economy

import com.y54895.matrixlib.api.resource.MatrixResourceFiles
import com.y54895.matrixlib.api.text.MatrixText
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.Plugin
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import taboolib.common.platform.function.warning
import taboolib.platform.BukkitPlugin
import java.io.File
import kotlin.math.ceil

data class MatrixCurrencyDefinition(
    val key: String,
    val mode: MatrixCurrencyMode,
    val displayName: String,
    val symbol: String,
    val decimal: Boolean,
    val placeholder: String = "",
    val takeActions: List<String> = emptyList(),
    val giveActions: List<String> = emptyList(),
    val denyActions: List<String> = emptyList()
)

enum class MatrixCurrencyMode {
    VAULT,
    PLAYERPOINTS,
    PLACEHOLDER
}

data class MatrixCurrencyPrice(
    val currencyKey: String,
    val amount: Double
)

data class MatrixCurrencyShortage(
    val key: String,
    val displayName: String,
    val needAmount: Double,
    val balance: Double,
    val formattedNeed: String,
    val formattedBalance: String,
    val denyHandled: Boolean
)

data class MatrixChargeResult(
    val success: Boolean,
    val shortages: List<MatrixCurrencyShortage> = emptyList(),
    val failedCurrency: String? = null
)

object MatrixEconomy {

    private val currencies = LinkedHashMap<String, MatrixCurrencyDefinition>()

    fun reload() {
        currencies.clear()
        MatrixVaultEconomyBridge.reload()
        MatrixPlayerPointsBridge.reload()
        val file = syncCurrencyFiles()
        if (!file.exists()) {
            currencies["vault"] = MatrixCurrencyDefinition("vault", MatrixCurrencyMode.VAULT, "Vault", "", true)
            return
        }
        val yaml = YamlConfiguration.loadConfiguration(file)
        yaml.getKeys(false).forEach { key ->
            val section = yaml.getConfigurationSection(key) ?: return@forEach
            val definition = parseDefinition(key, section) ?: return@forEach
            currencies[definition.key.lowercase()] = definition
        }
        if (currencies.isEmpty()) {
            warning("MatrixLib economy config is empty. Fallback currency 'vault' will be used.")
            currencies["vault"] = MatrixCurrencyDefinition("vault", MatrixCurrencyMode.VAULT, "Vault", "", true)
        }
    }

    fun configFile(): File {
        return MatrixResourceFiles.dataFile(BukkitPlugin.getInstance(), "Economy/currency.yml")
    }

    fun syncCurrencyFiles(): File {
        val plugin = BukkitPlugin.getInstance()
        val resourcePath = "Economy/currency.yml"
        MatrixResourceFiles.saveResourceIfAbsent(plugin, resourcePath)
        val canonical = MatrixResourceFiles.dataFile(plugin, resourcePath)
        val targets = linkedSetOf<File>()
        targets += canonical
        syncTargetPlugin("MatrixShop")?.dataFolder?.let { targets += File(it, resourcePath) }
        syncTargetPlugin("MatrixStorage")?.dataFolder?.let { targets += File(it, resourcePath) }

        val source = targets.filter(File::exists).maxByOrNull(File::lastModified) ?: canonical
        val content = if (source.exists()) source.readText() else ""
        if (content.isBlank()) {
            return canonical
        }
        targets.forEach { target ->
            target.parentFile?.mkdirs()
            if (!target.exists() || target.readText() != content) {
                target.writeText(content)
            }
        }
        return canonical
    }

    fun configuredKey(section: ConfigurationSection, path: String = "Currency", fallback: String = "vault"): String {
        return configuredKeyOrNull(section, path) ?: fallback
    }

    fun configuredKeyOrNull(section: ConfigurationSection, path: String = "Currency"): String? {
        return section.getString("$path.Key")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: section.getString("$path.Default")
                ?.trim()
                ?.takeIf(String::isNotBlank)
            ?: section.getString(path)
                ?.trim()
                ?.takeIf(String::isNotBlank)
    }

    fun allKeys(): List<String> = currencies.keys.toList()

    fun configuredCurrencyCount(): Int = currencies.size

    fun currencyModeDistribution(): Map<String, Int> {
        return currencies.values.groupingBy { it.mode.name.lowercase() }.eachCount().toSortedMap()
    }

    fun displayName(key: String?): String {
        val resolved = resolve(key)
        return definition(resolved)?.displayName ?: resolved
    }

    fun providerSummary(): String {
        return currencies.values.joinToString(", ") { definition ->
            "${definition.key}=${providerStatus(definition)}"
        }
    }

    fun formatAmount(key: String?, amount: Double): String {
        val definition = definition(resolve(key))
        return if (definition != null && !definition.decimal) {
            ceil(amount).toInt().toString()
        } else if (amount % 1.0 == 0.0) {
            amount.toInt().toString()
        } else {
            "%.2f".format(amount)
        }
    }

    fun balance(player: OfflinePlayer, key: String?): Double {
        val definition = definition(resolve(key)) ?: return 0.0
        return when (definition.mode) {
            MatrixCurrencyMode.VAULT -> MatrixVaultEconomyBridge.balance(player)
            MatrixCurrencyMode.PLAYERPOINTS -> MatrixPlayerPointsBridge.balance(player)
            MatrixCurrencyMode.PLACEHOLDER -> MatrixPlaceholderBridge.balance(player, definition.placeholder)
        }
    }

    fun has(player: OfflinePlayer, key: String?, amount: Double): Boolean {
        if (amount <= 0) {
            return true
        }
        return balance(player, key) >= normalizedAmount(key, amount)
    }

    fun isAvailable(key: String?): Boolean {
        val definition = definition(resolve(key)) ?: return false
        return when (definition.mode) {
            MatrixCurrencyMode.VAULT -> MatrixVaultEconomyBridge.isAvailable()
            MatrixCurrencyMode.PLAYERPOINTS -> MatrixPlayerPointsBridge.isAvailable()
            MatrixCurrencyMode.PLACEHOLDER -> definition.placeholder.isNotBlank()
        }
    }

    fun shortage(player: Player, key: String?, needAmount: Double, extra: Map<String, String> = emptyMap()): MatrixCurrencyShortage {
        val resolved = resolve(key)
        val definition = definition(resolved)
        val currentBalance = balance(player, resolved)
        val placeholders = buildPlaceholders(player, resolved, needAmount, currentBalance, extra)
        val denyHandled = definition != null && definition.denyActions.isNotEmpty().also { handled ->
            if (handled) {
                executeActions(player, definition.denyActions, placeholders)
            }
        }
        return MatrixCurrencyShortage(
            key = resolved,
            displayName = displayName(resolved),
            needAmount = needAmount,
            balance = currentBalance,
            formattedNeed = formatAmount(resolved, needAmount),
            formattedBalance = formatAmount(resolved, currentBalance),
            denyHandled = denyHandled
        )
    }

    fun withdraw(player: OfflinePlayer, key: String?, amount: Double, extra: Map<String, String> = emptyMap()): Boolean {
        if (amount <= 0) {
            return true
        }
        val resolved = resolve(key)
        val definition = definition(resolved) ?: return false
        return when (definition.mode) {
            MatrixCurrencyMode.VAULT -> MatrixVaultEconomyBridge.withdraw(player, normalizedAmount(resolved, amount))
            MatrixCurrencyMode.PLAYERPOINTS -> MatrixPlayerPointsBridge.withdraw(player, normalizedAmount(resolved, amount))
            MatrixCurrencyMode.PLACEHOLDER -> {
                if (!has(player, resolved, amount)) {
                    false
                } else {
                    executeActions(player, definition.takeActions, buildPlaceholders(player, resolved, amount, balance(player, resolved), extra))
                    definition.takeActions.isNotEmpty()
                }
            }
        }
    }

    fun deposit(player: OfflinePlayer, key: String?, amount: Double, extra: Map<String, String> = emptyMap()): Boolean {
        if (amount <= 0) {
            return true
        }
        val resolved = resolve(key)
        val definition = definition(resolved) ?: return false
        return when (definition.mode) {
            MatrixCurrencyMode.VAULT -> MatrixVaultEconomyBridge.deposit(player, normalizedAmount(resolved, amount))
            MatrixCurrencyMode.PLAYERPOINTS -> MatrixPlayerPointsBridge.deposit(player, normalizedAmount(resolved, amount))
            MatrixCurrencyMode.PLACEHOLDER -> {
                executeActions(player, definition.giveActions, buildPlaceholders(player, resolved, amount, balance(player, resolved), extra))
                definition.giveActions.isNotEmpty()
            }
        }
    }

    fun checkPrices(player: Player, prices: List<MatrixCurrencyPrice>, extra: Map<String, String> = emptyMap()): MatrixChargeResult {
        val shortages = prices
            .filter { it.amount > 0.0 }
            .mapNotNull { price ->
                if (!isAvailable(price.currencyKey) || !has(player, price.currencyKey, price.amount)) {
                    shortage(player, price.currencyKey, price.amount, extra)
                } else {
                    null
                }
            }
        return MatrixChargeResult(shortages.isEmpty(), shortages)
    }

    fun charge(player: Player, prices: List<MatrixCurrencyPrice>, extra: Map<String, String> = emptyMap()): MatrixChargeResult {
        val check = checkPrices(player, prices, extra)
        if (!check.success) {
            return check
        }
        val paid = arrayListOf<MatrixCurrencyPrice>()
        prices.filter { it.amount > 0.0 }.forEach { price ->
            if (!withdraw(player, price.currencyKey, price.amount, extra)) {
                paid.forEach { refund ->
                    deposit(player, refund.currencyKey, refund.amount, extra)
                }
                return MatrixChargeResult(false, failedCurrency = price.currencyKey)
            }
            paid += price
        }
        return MatrixChargeResult(true)
    }

    private fun resolve(key: String?): String {
        val normalized = key?.trim()?.takeIf(String::isNotBlank)?.lowercase()
        return when {
            normalized == null -> "vault"
            currencies.containsKey(normalized) -> normalized
            else -> "vault"
        }
    }

    private fun definition(key: String): MatrixCurrencyDefinition? {
        return currencies[key.lowercase()]
    }

    private fun normalizedAmount(key: String?, amount: Double): Double {
        val definition = definition(resolve(key))
        return if (definition != null && !definition.decimal) ceil(amount) else amount
    }

    private fun providerStatus(definition: MatrixCurrencyDefinition): String {
        return when (definition.mode) {
            MatrixCurrencyMode.VAULT -> MatrixVaultEconomyBridge.providerName()
            MatrixCurrencyMode.PLAYERPOINTS -> MatrixPlayerPointsBridge.providerName()
            MatrixCurrencyMode.PLACEHOLDER -> if (definition.placeholder.isNotBlank()) "placeholder" else "invalid"
        }
    }

    private fun parseDefinition(key: String, section: ConfigurationSection): MatrixCurrencyDefinition? {
        val rawMode = section.getString("Mode")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return null
        val mode = when {
            rawMode.equals("vault", true) -> MatrixCurrencyMode.VAULT
            rawMode.equals("playerpoints", true) -> MatrixCurrencyMode.PLAYERPOINTS
            else -> MatrixCurrencyMode.PLACEHOLDER
        }
        val placeholder = if (mode == MatrixCurrencyMode.PLACEHOLDER) {
            section.getString("Placeholder")
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: rawMode
        } else {
            ""
        }
        return MatrixCurrencyDefinition(
            key = key.lowercase(),
            mode = mode,
            displayName = section.getString("Display-Name", key).orEmpty(),
            symbol = section.getString("Symbol", "").orEmpty(),
            decimal = section.getBoolean("Decimal", mode != MatrixCurrencyMode.PLAYERPOINTS),
            placeholder = placeholder,
            takeActions = loadActions(section, "Take"),
            giveActions = loadActions(section, "Give"),
            denyActions = loadActions(section, "Deny")
        )
    }

    private fun loadActions(section: ConfigurationSection, key: String): List<String> {
        val directList = section.getStringList(key).mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
        if (directList.isNotEmpty()) {
            return directList
        }
        val directSingle = section.getString(key)?.trim()?.takeIf(String::isNotBlank)
        if (directSingle != null) {
            return listOf(directSingle)
        }
        val nestedPath = "Actions.$key"
        val nestedList = section.getStringList(nestedPath).mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
        if (nestedList.isNotEmpty()) {
            return nestedList
        }
        return section.getString(nestedPath)?.trim()?.takeIf(String::isNotBlank)?.let(::listOf) ?: emptyList()
    }

    private fun buildPlaceholders(
        player: OfflinePlayer,
        key: String,
        amount: Double,
        balance: Double,
        extra: Map<String, String>
    ): Map<String, String> {
        return linkedMapOf(
            "player" to (player.name ?: ""),
            "sender" to (player.name ?: ""),
            "currency" to displayName(key),
            "money" to formatAmount(key, amount),
            "amount" to formatAmount(key, amount),
            "need" to formatAmount(key, amount),
            "need-money" to formatAmount(key, amount),
            "balance" to formatAmount(key, balance)
        ) + extra
    }

    private fun executeActions(player: OfflinePlayer, actions: List<String>, placeholders: Map<String, String>) {
        actions.forEach { raw ->
            val action = MatrixText.apply(raw, placeholders).trim()
            when {
                action.isBlank() -> Unit
                action.startsWith("tell:", true) -> onlinePlayer(player)?.sendMessage(MatrixText.color(action.substringAfter(':').trim()))
                action.startsWith("player:", true) -> onlinePlayer(player)?.performCommand(action.substringAfter(':').trim().removePrefix("/"))
                action.startsWith("console:", true) -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), action.substringAfter(':').trim().removePrefix("/"))
                else -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), action.removePrefix("/"))
            }
        }
    }

    private fun onlinePlayer(player: OfflinePlayer): Player? {
        return if (player is Player && player.isOnline) player else player.player
    }

    private fun syncTargetPlugin(name: String): Plugin? {
        return Bukkit.getPluginManager().getPlugin(name)
    }
}

private object MatrixPlaceholderBridge {

    private var apiClass: Class<*>? = null

    private fun ensureClass(): Class<*>? {
        if (apiClass != null) {
            return apiClass
        }
        apiClass = runCatching { Class.forName("me.clip.placeholderapi.PlaceholderAPI") }.getOrNull()
        return apiClass
    }

    fun balance(player: OfflinePlayer, placeholder: String): Double {
        val online = if (player is Player && player.isOnline) player else player.player ?: return 0.0
        val clazz = ensureClass() ?: return 0.0
        val method = clazz.methods.firstOrNull {
            it.name == "setPlaceholders" &&
                it.parameterTypes.size == 2 &&
                Player::class.java.isAssignableFrom(it.parameterTypes[0]) &&
                it.parameterTypes[1] == String::class.java
        } ?: return 0.0
        val raw = runCatching { method.invoke(null, online, placeholder) as? String }.getOrNull() ?: return 0.0
        return raw.toDoubleOrNull()
            ?: raw.replace(",", "").toDoubleOrNull()
            ?: Regex("""-?\d+(?:\.\d+)?""").find(raw)?.value?.toDoubleOrNull()
            ?: 0.0
    }
}

private object MatrixPlayerPointsBridge {

    private var api: Any? = null

    fun reload() {
        api = null
        val plugin = Bukkit.getPluginManager().getPlugin("PlayerPoints") ?: return
        api = runCatching { plugin.javaClass.getMethod("getAPI").invoke(plugin) }.getOrNull()
    }

    fun providerName(): String {
        return if (api != null) "PlayerPoints" else "none"
    }

    fun isAvailable(): Boolean = api != null

    fun balance(player: OfflinePlayer): Double {
        val provider = api ?: return 0.0
        return invokeNumber(provider, "look", player, player.uniqueId)?.toDouble() ?: 0.0
    }

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean {
        val provider = api ?: return false
        return invokeBoolean(provider, "take", player, ceil(amount).toInt())
    }

    fun deposit(player: OfflinePlayer, amount: Double): Boolean {
        val provider = api ?: return false
        return invokeBoolean(provider, "give", player, ceil(amount).toInt())
    }

    private fun invokeNumber(target: Any, methodName: String, player: OfflinePlayer, uuid: java.util.UUID): Number? {
        val methods = target.javaClass.methods.filter { it.name == methodName && it.parameterTypes.size == 1 }
        methods.forEach { method ->
            val parameter = when {
                method.parameterTypes[0].isInstance(uuid) -> uuid
                method.parameterTypes[0] == String::class.java -> player.name ?: return@forEach
                else -> return@forEach
            }
            val result = runCatching { method.invoke(target, parameter) }.getOrNull()
            if (result is Number) {
                return result
            }
        }
        return null
    }

    private fun invokeBoolean(target: Any, methodName: String, player: OfflinePlayer, amount: Int): Boolean {
        val methods = target.javaClass.methods.filter { it.name == methodName && it.parameterTypes.size == 2 }
        methods.forEach { method ->
            val first = when {
                method.parameterTypes[0].isInstance(player.uniqueId) -> player.uniqueId
                method.parameterTypes[0] == String::class.java -> player.name ?: return@forEach
                else -> return@forEach
            }
            val second = when (method.parameterTypes[1]) {
                Int::class.java, Integer.TYPE -> amount
                Double::class.java, java.lang.Double.TYPE -> amount.toDouble()
                else -> return@forEach
            }
            val result = runCatching { method.invoke(target, first, second) }.getOrNull()
            if (result is Boolean) {
                return result
            }
            if (result is Number) {
                return result.toInt() >= 0
            }
        }
        return false
    }
}

private object MatrixVaultEconomyBridge {

    private var economy: Any? = null
    private var economyClass: Class<*>? = null

    fun reload() {
        economy = null
        economyClass = null
        runCatching {
            val clazz = Class.forName("net.milkbowl.vault.economy.Economy")
            val registration = Bukkit.getServicesManager().getRegistration(clazz)
            economyClass = clazz
            economy = registration?.provider
        }.onFailure {
            warning("Vault API not found. Shared paid flows will require Vault before they can work.")
        }
    }

    fun providerName(): String {
        return economy?.javaClass?.simpleName ?: "none"
    }

    fun isAvailable(): Boolean {
        return economy != null && economyClass != null
    }

    fun balance(player: OfflinePlayer): Double {
        val provider = economy ?: return 0.0
        return invokeDouble(provider, "getBalance", player)
    }

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean {
        if (amount <= 0) {
            return true
        }
        val response = invokeEconomy("withdrawPlayer", player, amount) ?: return false
        return transactionSuccess(response)
    }

    fun deposit(player: OfflinePlayer, amount: Double): Boolean {
        if (amount <= 0) {
            return true
        }
        val response = invokeEconomy("depositPlayer", player, amount) ?: return false
        return transactionSuccess(response)
    }

    private fun invokeDouble(target: Any, methodName: String, vararg args: Any): Double {
        return (invokeTarget(target, methodName, *args) as? Number)?.toDouble() ?: 0.0
    }

    private fun invokeBoolean(target: Any, methodName: String, vararg args: Any): Boolean {
        return invokeTarget(target, methodName, *args) as? Boolean ?: false
    }

    private fun invokeEconomy(methodName: String, vararg args: Any): Any? {
        val provider = economy ?: return null
        return invokeTarget(provider, methodName, *args)
    }

    private fun invokeTarget(target: Any, methodName: String, vararg args: Any): Any? {
        val methods = target.javaClass.methods.filter { it.name == methodName && it.parameterTypes.size == args.size }
        methods.forEach { method ->
            val adapted = adaptArguments(method.parameterTypes, args) ?: return@forEach
            val result = runCatching { method.invoke(target, *adapted) }.getOrNull()
            if (result != null) {
                return result
            }
        }
        return null
    }

    private fun adaptArguments(types: Array<Class<*>>, args: Array<out Any>): Array<Any?>? {
        val adapted = arrayOfNulls<Any>(args.size)
        for (index in args.indices) {
            val arg = args[index]
            val type = types[index]
            adapted[index] = when {
                type.isInstance(arg) -> arg
                arg is OfflinePlayer && type == String::class.java -> arg.name ?: return null
                arg is Player && type == String::class.java -> arg.name
                arg is Number && (type == Double::class.java || type == java.lang.Double.TYPE) -> arg.toDouble()
                else -> return null
            }
        }
        return adapted
    }

    private fun transactionSuccess(response: Any): Boolean {
        return when (response) {
            is Boolean -> response
            else -> invokeBoolean(response, "transactionSuccess")
        }
    }
}
