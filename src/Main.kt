import java.io.File
import kotlin.random.Random

data class LineParseResult(val fromValues: List<FromString>, val toValues: List<String>)
data class FromString(val string: String, val allowNoWhitespace: Boolean)
data class ReplacementList(val replacements: List<String>, val allowNoWhitespace: Boolean)
data class ReplacementCandidate(val fromString: String, val replacements: ReplacementList, val index: Int)

const val PREFIX_CHANCE = 0.3f
const val POSTFIX_CHANCE = 0.3f
const val REPLACEMENT_CHANCE = 0.5f
const val REPLACEMENT_CHANCE_NO_WHITESPACE = 0.1f

fun randomChance(chance: Float): Boolean {
	return Random.nextFloat() <= chance
}

object RoleplayData {
	var replacements: MutableMap<String, ReplacementList> = mutableMapOf()
	var prefixes: MutableList<String> = mutableListOf()
	var postfixes: MutableList<String> = mutableListOf()

	var insultsAdjectives: MutableList<String> = mutableListOf()
	var insultsNouns: MutableList<String> = mutableListOf()

	var bodyParts: MutableList<String> = mutableListOf()
	var bodyPartAdjectives: MutableList<String> = mutableListOf()
	var gods: MutableList<String> = mutableListOf()
	var godAdjectives: MutableList<String> = mutableListOf()

	var maxReplacedLength = 0
	fun updateMaxLength(text: String) {
		if (text.length > maxReplacedLength) {
			maxReplacedLength = text.length
		}
	}

	val fileFetchReplacements = mapOf(
		"<god>" to gods,
		"<goda>" to godAdjectives,
		"<bop>" to bodyParts,
		"<bopa>" to bodyPartAdjectives,
		"<adj>" to insultsAdjectives,
		"<nou>" to insultsNouns,
	)
}

fun readRoleplayLine(line: String, startInFromValue: Boolean): LineParseResult {
	val fromValues: MutableList<FromString> = mutableListOf()
	val toValues: MutableList<String> = mutableListOf()

	val currentValue = StringBuilder()
	var inQuotes = false
	var inFromValue = startInFromValue
	var currentAllowNoWhitespace = false

	fun addCurrentValue() {
		if (inFromValue) {
			fromValues.add(FromString(currentValue.toString(), currentAllowNoWhitespace))
		} else {
			toValues.add(currentValue.toString())
		}
	}

	for (char in line) {
		when (char) {
			',' -> {
				if (!inQuotes) {
					if (currentValue.isNotEmpty()) {
						addCurrentValue()

						inFromValue = false
						currentAllowNoWhitespace = false
						currentValue.clear()
					} else {
						break
					}
				} else {
					currentValue.append(char)
				}
			}

			'"' -> inQuotes = !inQuotes
			'#' -> inFromValue = true
			'$' -> currentAllowNoWhitespace = true
			else -> {
				currentValue.append(char)
			}
		}
	}

	if (currentValue.isNotEmpty()) {
		addCurrentValue()
	}

	return LineParseResult(fromValues, toValues)
}

fun readRoleplayData(file: String, intoList: MutableList<String>?, intoMap: MutableMap<String, ReplacementList>?) {
	val reader = File(file).bufferedReader()
	reader.forEachLine { line ->
		if (line.isNotEmpty()) {
			val lineData = readRoleplayLine(line, intoMap != null)
			if (intoMap != null) {
				for (entry in lineData.fromValues) {
					intoMap[entry.string] = ReplacementList(lineData.toValues, entry.allowNoWhitespace)
					RoleplayData.updateMaxLength(entry.string)
				}
			} else {
				intoList?.addAll(lineData.toValues)
			}
		}
	}
}

fun main(args: Array<String>) {
	if (args.isEmpty()) {
		return
	}

	readRoleplayData("replacements.csv", null, RoleplayData.replacements)
	readRoleplayData("prefixes.csv", RoleplayData.prefixes, null)
	readRoleplayData("postfixes.csv", RoleplayData.postfixes, null)

	readRoleplayData("insults_adjectives.csv", RoleplayData.insultsAdjectives, null)
	readRoleplayData("insults_nouns.csv", RoleplayData.insultsNouns, null)
	readRoleplayData("body_parts.csv", RoleplayData.bodyParts, null)
	readRoleplayData("body_part_adjectives.csv", RoleplayData.bodyPartAdjectives, null)
	readRoleplayData("gods.csv", RoleplayData.gods, null)
	readRoleplayData("god_adjectives.csv", RoleplayData.godAdjectives, null)

	var inText = StringBuilder(args[0])

	var outerIndex = 0
	while (outerIndex < inText.length) {
		val currentReplaced = StringBuilder()
		var currentReplacedCandidate: ReplacementCandidate? = null
		for (innerIndex in 0..<RoleplayData.maxReplacedLength) {
			val totalIndex = outerIndex + innerIndex
			if (totalIndex >= inText.length) {
				break
			}

			val char = inText[totalIndex]
			currentReplaced.append(char)
			val currentReplacedString = currentReplaced.toString().lowercase()
			val replacements = RoleplayData.replacements[currentReplacedString]
			if (replacements != null) {
				val startIndex = totalIndex - currentReplacedString.length + 1

				val atLineStart = startIndex - 1 < 0
				val atLineEnd = startIndex + 1 >= inText.length
				if (replacements.allowNoWhitespace || ((atLineStart || inText[startIndex - 1].isWhitespace()) && (atLineEnd || inText[totalIndex + 1].isWhitespace()))) {
					currentReplacedCandidate = ReplacementCandidate(currentReplacedString, replacements, startIndex)
				}
			}
		}

		if (currentReplacedCandidate != null) {
			val replacementChance = if (currentReplacedCandidate.replacements.allowNoWhitespace) REPLACEMENT_CHANCE_NO_WHITESPACE else REPLACEMENT_CHANCE
			if (randomChance(replacementChance)) {
				val replacement = currentReplacedCandidate.replacements.replacements.random()

				inText.delete(currentReplacedCandidate.index, currentReplacedCandidate.index + currentReplacedCandidate.fromString.length)
				inText.insert(currentReplacedCandidate.index, replacement)

				outerIndex = currentReplacedCandidate.index + replacement.length
			}
		}

		++outerIndex
	}

	if (randomChance(PREFIX_CHANCE)) {
		inText.insert(0, RoleplayData.prefixes.random())
	}

	if (randomChance(POSTFIX_CHANCE)) {
		when (inText.last()) {
			'.', ',', '!', '?' -> {}
			else -> inText.append('.')
		}

		inText.append(RoleplayData.postfixes.random())
	}

	for (entry in RoleplayData.fileFetchReplacements) {
		while (inText.contains(entry.key)) {
			inText = StringBuilder(inText.replaceFirst(entry.key.toRegex(), entry.value.random()))
		}
	}

	println(inText.toString())
}
