import sttp.client3._
import io.circe.parser._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

import scala.concurrent.duration.{FiniteDuration, SECONDS}

/** Case class representing a query term */
case class QueryTerm(id: Int, target: Int, text: String, language: String, keepOrder: Boolean)

/** Case class representing an alert with its contents */
case class Alert(id: String, contents: List[Content], date: String, inputType: String)

/** Case class representing the content of an alert */
case class Content(text: String, `type`: String, language: String)

/** Case class representing a match between an alert and a query term */
case class Match(alertId: String, queryTermId: Int)

/**
 * Object to perform alert extraction based on query terms.
 */
object AlertExtractor {
  private val logger = LoggerFactory.getLogger(AlertExtractor.getClass)
  private val options = SttpBackendOptions.connectionTimeout(FiniteDuration(30, SECONDS)) // 30 seconds
  implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend(options)

  // API key and endpoints
  // API key and URLs could be set in configuration like env var or database entry to be more secure
  private val apiKey: String = "marco:3c9a661369b9d1f430759ed62f6193779d2f4893c0c0c2757c8983cb2d5f9ef3"
  private val queryTermsUrl: String = s"https://services.prewave.ai/adminInterface/api/testQueryTerm?key=$apiKey"
  private val alertsUrl: String = s"https://services.prewave.ai/adminInterface/api/testAlerts?key=$apiKey"

  /** Fetches query terms from the API and parses to a list of QueryTerm */
  private def fetchQueryTerms(): List[QueryTerm] = {
    try {
      val response = basicRequest
        .get(uri"$queryTermsUrl")
        .response(asString)
        .send(backend)

      logger.debug(s"Query Terms HTTP Status Code: ${response.code}")

      response.body match {
        case Right(data) =>
          val terms = decode[List[QueryTerm]](data).getOrElse(Nil)
          logger.debug(s"Fetched ${terms.size} query terms")
          terms
        case Left(error) =>
          logger.error(s"Error fetching query terms: $error")
          Nil
      }
    } catch {
      case e: Exception =>
        logger.error(s"Exception when fetching query terms: ${e.getMessage}")
        e.printStackTrace()
        Nil
    }
  }

  /** Fetches alerts from the API and parses to a list of Alert */
  private def fetchAlerts(): List[Alert] = {
    try {
      val response = basicRequest
        .get(uri"$alertsUrl")
        .response(asString)
        .send(backend)

      logger.debug(s"Alerts HTTP Status Code: ${response.code}")

      response.body match {
        case Right(data) =>
          val alerts = decode[List[Alert]](data).getOrElse(Nil)
          logger.debug(s"Fetched ${alerts.size} alerts")
          alerts
        case Left(error) =>
          logger.error(s"Error fetching alerts: $error")
          Nil
      }
    } catch {
      case e: Exception =>
        logger.error(s"Exception when fetching alerts: ${e.getMessage}")
        e.printStackTrace()
        Nil
    }
  }

  /**
   * Checks if a query term is present in alert content.
   * @param queryText The text of the query term
   * @param alertText The text of the alert content
   * @param keepOrder Boolean indicating if order must be maintained
   * @return True if there is a match, false otherwise
   */
  def isMatch(queryText: String, alertText: String, keepOrder: Boolean): Boolean = {
    val queryParts = queryText.toLowerCase.split("\\s+").filter(_.nonEmpty)
    val normalizedAlertText = alertText.toLowerCase()

    if (keepOrder) {
      normalizedAlertText.contains(queryParts.mkString(" "))
    } else {
      // For large texts, this approach can be more efficient
      val alertWords = normalizedAlertText.split("\\s+").toSet
      queryParts.forall(alertWords.contains)
    }
  }

  /**
   * Find matches between query terms and alerts
   *
   * Benefits of this approach:
   * Early filtering: Reduces the working set at each step
   * Indexed lookups: Using a map for language-based filtering
   * Memory efficiency: Avoids creating intermediate collections for non-matches
   *
   * Note: For very large datasets, we might also consider using Akka Streams or Futures for Asynchronous Processing
   */
  def findMatches(queryTerms: List[QueryTerm], alerts: List[Alert]): List[Match] = {
    if (queryTerms.isEmpty || alerts.isEmpty) {
      logger.error("Failed to fetch data from APIs. Please check your API key.")
    }

    // Pre-group query terms by language for faster lookups
    val queryTermsByLanguage = queryTerms.groupBy(_.language.toLowerCase)

    // Use flatMap for early filtering
    val matches = alerts.flatMap { alert =>
      alert.contents.flatMap { content =>
        // Only look at query terms matching this content's language
        queryTermsByLanguage.getOrElse(content.language.toLowerCase, Nil)
          .filter(queryTerm => isMatch(queryTerm.text, content.text, queryTerm.keepOrder))
          .map(queryTerm => Match(alert.id, queryTerm.id))
      }
    }.distinct

    if (matches.nonEmpty) {
      logger.debug(s"Found ${matches.size} unique matches:")
      matches.foreach(m => logger.info(s"Alert ID: ${m.alertId}, Query Term ID: ${m.queryTermId}"))
    }

    matches
  }

  /** Main execution function */
  def main(args: Array[String]): Unit = {
    val queryTerms = fetchQueryTerms()
    val alerts = fetchAlerts()

    findMatches(queryTerms, alerts)
  }
}