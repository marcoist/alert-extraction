import sttp.client3._
import io.circe.parser._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory
import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.util.{Try, Success, Failure}

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

  // Base URLs - API key will be appended
  private val queryTermsUrlBase: String = "https://services.prewave.ai/adminInterface/api/testQueryTerm"
  private val alertsUrlBase: String = "https://services.prewave.ai/adminInterface/api/testAlerts"

  /** Fetches query terms from the API and parses to a list of QueryTerm */
  private def fetchQueryTerms(apiKey: String): List[QueryTerm] = {
    val url = uri"$queryTermsUrlBase?key=$apiKey"
    logger.debug(s"Fetching query terms from: $queryTermsUrlBase")
    Try {
      val response = basicRequest
        .get(url)
        .response(asString)
        .send(backend)

      logger.debug(s"Query Terms HTTP Status Code: ${response.code}")

      response.body match {
        case Right(data) =>
          decode[List[QueryTerm]](data) match {
            case Right(terms) =>
              logger.info(s"Successfully fetched and parsed ${terms.size} query terms.")
              terms
            case Left(decodingError) =>
              logger.error(s"Failed to decode query terms JSON: ${decodingError.getMessage}", decodingError)
              Nil
          }
        case Left(error) =>
          logger.error(s"Error fetching query terms (HTTP level): $error")
          Nil
      }
    } match {
      case Success(result) => result
      case Failure(e) =>
        logger.error(s"Exception occurred when fetching query terms: ${e.getMessage}", e)
        Nil
    }
  }

  /** Fetches alerts from the API and parses to a list of Alert */
  private def fetchAlerts(apiKey: String): List[Alert] = {
    val url = uri"$alertsUrlBase?key=$apiKey"
    logger.debug(s"Fetching alerts from: $alertsUrlBase")
    Try {
      val response = basicRequest
        .get(url)
        .response(asString)
        .send(backend)

      logger.debug(s"Alerts HTTP Status Code: ${response.code}")

      response.body match {
        case Right(data) =>
          decode[List[Alert]](data) match {
            case Right(alerts) =>
              logger.info(s"Successfully fetched and parsed ${alerts.size} alerts.")
              alerts
            case Left(decodingError) =>
              logger.error(s"Failed to decode alerts JSON: ${decodingError.getMessage}", decodingError)
              Nil
          }
        case Left(error) =>
          logger.error(s"Error fetching alerts (HTTP level): $error")
          Nil
      }
    } match {
      case Success(result) => result
      case Failure(e) =>
        logger.error(s"Exception occurred when fetching alerts: ${e.getMessage}", e)
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
    // Note: Basic tokenization using split("\\s+"). This might not robustly handle punctuation
    // attached to words (e.g., "Metall," vs "Metall"). More advanced tokenization could be used if needed.
    val normalizedAlertText = alertText.toLowerCase()

    if (queryParts.isEmpty) {
      false
    } else if (keepOrder) {
      // Check for the exact sequence of words
      normalizedAlertText.contains(queryParts.mkString(" "))
    } else {
      // Check if all query parts exist anywhere in the text
      // Using a Set for alert words offers efficient lookups (O(1) average)
      val alertWords = normalizedAlertText.split("\\s+").toSet
      queryParts.forall(alertWords.contains)
    }
  }

  /**
   * Find matches between query terms and alerts
   */
  def findMatches(queryTerms: List[QueryTerm], alerts: List[Alert]): List[Match] = {
    if (queryTerms.isEmpty) {
      logger.warn("Query terms list is empty. No matches possible.")
      return Nil
    }
    if (alerts.isEmpty) {
      logger.warn("Alerts list is empty. No matches possible.")
      return Nil
    }

    // Pre-group query terms by language (case-insensitive) for faster lookups
    val queryTermsByLanguage = queryTerms.groupBy(_.language.toLowerCase)
    logger.debug(s"Grouped query terms by languages: ${queryTermsByLanguage.keys.mkString(", ")}")

    val matches = alerts.flatMap { alert =>
      alert.contents.flatMap { content =>
        // Efficiently get relevant query terms based on content language
        queryTermsByLanguage.getOrElse(content.language.toLowerCase, Nil)
          // Filter this subset of terms by checking if they match the content text
          .filter(queryTerm => isMatch(queryTerm.text, content.text, queryTerm.keepOrder))
          // Map successful matches to the Match case class
          .map(queryTerm => Match(alert.id, queryTerm.id))
      }
    }.distinct // Ensure uniqueness as required

    logger.info(s"Found ${matches.size} unique matches.")
    matches
  }

  /** Main execution function */
  def main(args: Array[String]): Unit = {
    // --- API Key Handling ---
    val apiKeyOpt: Option[String] = sys.env.get("PREWAVE_API_KEY")

    apiKeyOpt match {
      case Some(apiKey) if apiKey.nonEmpty =>
        logger.info("API Key found in environment variable PREWAVE_API_KEY.")

        // --- Fetch Data ---
        val queryTerms = fetchQueryTerms(apiKey)
        val alerts = fetchAlerts(apiKey)

        // --- Process Data ---
        if (queryTerms.nonEmpty && alerts.nonEmpty) {
          val foundMatches = findMatches(queryTerms, alerts)

          // --- Output Results ---
          println("-" * 30)
          println("Alert Term Extraction Results:")
          println("-" * 30)
          if (foundMatches.nonEmpty) {
            foundMatches.foreach { m =>
              println(s"Match Found => Alert ID: ${m.alertId}, Query Term ID: ${m.queryTermId}")
            }
          } else println("No matches found between the fetched alerts and query terms.")

          println("-" * 30)

        } else logger.error("Could not proceed with matching as fetching query terms or alerts failed or returned empty lists.")


      case _ =>
        logger.error("API Key not found or empty in environment variable PREWAVE_API_KEY.")
        sys.exit(1)
    }
    // Ensure backend is shut down cleanly (especially important for non-daemon thread pools in async backends)
    backend.close()
    logger.info("Processing finished.")
  }
}