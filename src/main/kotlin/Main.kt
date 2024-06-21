package org.example

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.math4.legacy.stat.descriptive.moment.StandardDeviation
import org.apache.commons.statistics.distribution.NormalDistribution
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileReader
import java.io.FileWriter
import java.time.LocalDate
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

val client = OkHttpClient()

fun main() {
    val tickerToUse = "IONQ"
    val daysToExp = 17.0
    val divRate = 0.0

    val prices = stockHistoryRequest(tickerToUse, daysToExp * 2).map { Price(it) }
    writeToFile(tickerToUse, prices)
    val logReturns = List(prices.size-1) { i -> ln(prices[i].price / prices[i + 1].price) }
    val option = blackScholesPut(
        logReturns.slice(0..20).toDoubleArray(),
        169.79,
        170.0,
        daysToExp /365,
        divRate
    )
    println(option)
}

fun stockHistoryRequest(ticker: String, timeHorizon: Double): DoubleArray {
    val request = Request.Builder()
        .url("https://financialmodelingprep.com/api/v3/historical-price-full/$ticker?from=${LocalDate.now().minusDays(timeHorizon.roundToLong())}&apikey=${System.getenv("FINANCIAL_MODELING")}")
        .build()
    var response: String
    client.newCall(request).execute().use {
        if (!it.isSuccessful) throw RuntimeException("Unexpected code $it")
        response = it.body!!.string()
    }
    val closes = Regex("close\": \\d*\\.\\d*,")
        .findAll(response)
        .map { it.value }
        .map { it.substring(it.indexOf(':')+1, it.indexOf(',')) }
        .toList()
    if (closes.isEmpty()) println("Unable to find closing prices\n$response")

    return closes.map { it.toDouble() }.toDoubleArray()
}

fun blackScholesPut(array: DoubleArray, underlying: Double, strike: Double, timeToExp: Double, dividendRate: Double = 0.0): Double {
    val sd = StandardDeviation(false).evaluate(array) * sqrt(252.0)
    println("Volatility: $sd")
    val d1 = (ln(underlying / strike) + timeToExp * (0.042 - dividendRate + sd.pow(2) / 2)) / (sd * sqrt(timeToExp))
    val d2 = d1 - sd * sqrt(timeToExp)
    return (strike * Math.E.pow(-0.042 * timeToExp) * NormalDistribution.of(0.0,1.0).cumulativeProbability(-d2)) -
            (underlying * Math.E.pow(-dividendRate * timeToExp) * NormalDistribution.of(0.0,1.0).cumulativeProbability(-d1))
}

fun writeToFile(title: String, obj: List<Price>) {
    BufferedWriter(FileWriter("src/main/resources/$title")).use {
        it.write(Json.encodeToString(obj))
    }
}

fun readFromFile(filename: String): List<Price> {
    BufferedReader(FileReader("src/main/resources/$filename")).use {
        return Json.decodeFromString<List<Price>>(it.readText())
    }
}

@Serializable
data class Price(val price: Double)