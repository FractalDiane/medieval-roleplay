import java.io.File

object RoleplayData {
	var replacements: MutableMap<String, List<String>> = mutableMapOf()
	var prefixes: MutableList<String> = mutableListOf()
	var postfixes: MutableList<String> = mutableListOf()

	var insultsAdjectives: MutableList<String> = mutableListOf()
	var insultsNouns: MutableList<String> = mutableListOf()

	var maxReplacedLength = 0
	fun updateMaxLength(text: String) {
		if (text.length > maxReplacedLength) {
			maxReplacedLength = text.length
		}
	}
}

data class LineParseResult(val fromValues: List<String>, val toValues: List<String>)

fun readRoleplayLine(line: String, startInFromValue: Boolean): LineParseResult {
	val fromValues: MutableList<String> = mutableListOf()
	val toValues: MutableList<String> = mutableListOf()

	val currentValue = StringBuilder()
	var inQuotes = false
	var inFromValue = startInFromValue
	for (char in line) {
		when (char) {
			',' -> {
				if (!inQuotes) {
					(if (inFromValue) fromValues else toValues).add(currentValue.toString())
					inFromValue = false
					currentValue.clear()
				} else {
					currentValue.append(char)
				}
			}

			'"' -> inQuotes = !inQuotes
			'#' -> inFromValue = true
			else -> {
				currentValue.append(char)
			}
		}
	}

	(if (inFromValue) fromValues else toValues).add(currentValue.toString())

	return LineParseResult(fromValues, toValues)
}

fun readRoleplayData(file: String, intoList: MutableList<String>?, intoMap: MutableMap<String, List<String>>?) {
	val readerReplacements = File(file).bufferedReader()
	readerReplacements.forEachLine { line ->
		if (line.isNotEmpty()) {
			val lineData = readRoleplayLine(line, intoMap != null)
			if (intoMap != null) {
				for (entry in lineData.fromValues) {
					intoMap[entry] = lineData.toValues
					RoleplayData.updateMaxLength(entry)
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

	var inText = args[0]

	var outerIndex = 0
	while (outerIndex < inText.length) {
		val currentReplaced = StringBuilder()
		var currentReplacedCandidate: Triple<String, List<String>, Int>? = null
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
				if ((startIndex - 1 < 0 || inText[startIndex - 1].isWhitespace()) && (totalIndex + 1 >= inText.length || inText[totalIndex + 1].isWhitespace())) {
					currentReplacedCandidate = Triple(currentReplacedString, replacements, startIndex)
				}
			}
		}

		if (currentReplacedCandidate != null) {
			val replacement = currentReplacedCandidate.second.random()

			val builder = StringBuilder(inText)
			builder.delete(currentReplacedCandidate.third, currentReplacedCandidate.third + currentReplacedCandidate.first.length)
			builder.insert(currentReplacedCandidate.third, replacement)

			inText = builder.toString()
			outerIndex = currentReplacedCandidate.third + replacement.length
		}

		++outerIndex
	}

	println(inText)
}
