import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach

class AlertExtractorSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  // Test data
  val sampleQueryTerms: List[QueryTerm] = List(
    QueryTerm(1, 100, "data breach", "en", keepOrder = true),
    QueryTerm(2, 200, "cyber attack", "en", keepOrder = true),
    QueryTerm(3, 300, "security incident", "en", keepOrder = false),
    QueryTerm(4, 400, "phishing email", "en", keepOrder = true),
    QueryTerm(5, 500, "datenverstoß", "de", keepOrder = true)
  )

  val sampleAlerts: List[Alert] = List(
    Alert("a1", List(Content("Company X suffered a major data breach yesterday", "TEXT", "en")), "2023-05-15", "RSS"),
    Alert("a2", List(Content("Security experts reported multiple cyber attack attempts", "TEXT", "en")), "2023-05-16", "RSS"),
    Alert("a3", List(Content("Incident regarding security measures was observed", "TEXT", "en")), "2023-05-17", "RSS"),
    Alert("a4", List(Content("Users received email phishing attempts", "TEXT", "en")), "2023-05-18", "RSS"),
    Alert("a5", List(Content("Ein schwerer Datenverstoß wurde gemeldet", "TEXT", "de")), "2023-05-19", "RSS"),
    Alert("a6", List(
      Content("First part of the content", "TEXT", "en"),
      Content("Second part mentions a data breach incident", "TEXT", "en")
    ), "2023-05-20", "RSS")
  )

  // Test exact matching with keepOrder = true
  "isMatch" should "match exact phrases when keepOrder is true" in {
    AlertExtractor.isMatch("data breach", "Company X suffered a major data breach yesterday", keepOrder = true) shouldBe true
    AlertExtractor.isMatch("cyber attack", "Security experts reported multiple cyber attack attempts", keepOrder = true) shouldBe true
    AlertExtractor.isMatch("data security", "Company X suffered a major data breach yesterday", keepOrder = true) shouldBe false
  }

  // Test for matching words in any order with keepOrder = false
  it should "match words in any order when keepOrder is false" in {
    AlertExtractor.isMatch("security incident", "Incident regarding security measures was observed", keepOrder = false) shouldBe true
    AlertExtractor.isMatch("incident security", "Incident regarding security measures was observed", keepOrder = false) shouldBe true
  }

  // Test case insensitivity
  it should "be case insensitive" in {
    AlertExtractor.isMatch("DATA BREACH", "Company X suffered a major data breach yesterday", keepOrder = true) shouldBe true
    AlertExtractor.isMatch("data breach", "Company X suffered a major DATA BREACH yesterday", keepOrder = true) shouldBe true
  }

  // Test with empty strings
  it should "handle empty strings correctly" in {
    AlertExtractor.isMatch("", "Some text", keepOrder = true) shouldBe false
    AlertExtractor.isMatch("data breach", "", keepOrder = true) shouldBe false  // Non-empty query doesn't match empty text
  }

  // Test with special characters
  it should "handle special characters appropriately" in {
    AlertExtractor.isMatch("data-breach", "Company suffered a data-breach yesterday", keepOrder = true) shouldBe true
    AlertExtractor.isMatch("data breach", "Company suffered a data-breach yesterday", keepOrder = true) shouldBe false
  }

  // Test matching in different languages
  it should "match different languages correctly" in {
    AlertExtractor.isMatch("datenverstoß", "Ein schwerer Datenverstoß wurde gemeldet", keepOrder = true) shouldBe true
  }

  // Test findMatches function
  "findMatches" should "correctly identify matches between query terms and alerts" in {
    val matches = AlertExtractor.findMatches(sampleQueryTerms, sampleAlerts)

    matches should contain (Match("a1", 1))  // data breach in alert a1
    matches should contain (Match("a2", 2))  // cyber attack in alert a2
    matches should contain (Match("a3", 3))  // security incident (words out of order) in alert a3
    matches should not contain Match("a4", 4) // "phishing email" != "email phishing" with keepOrder=true
    matches should contain (Match("a5", 5))  // datenverstoß in German alert
    matches should contain (Match("a6", 1))  // data breach in second content of alert a6

    matches.size shouldBe 5
  }

  it should "handle empty inputs correctly" in {
    AlertExtractor.findMatches(List(), sampleAlerts) shouldBe empty
    AlertExtractor.findMatches(sampleQueryTerms, List()) shouldBe empty
  }

  it should "not produce duplicate matches" in {
    val duplicateContentAlert = List(
      Alert("dup1", List(
        Content("This text contains data breach information", "TEXT", "en"),
        Content("This is another content with data breach mentioned", "TEXT", "en")
      ), "2023-05-21", "RSS")
    )

    val duplicateMatches = AlertExtractor.findMatches(
      List(QueryTerm(1, 100, "data breach", "en", keepOrder = true)),
      duplicateContentAlert
    )

    // Should only return one match despite the term appearing in two content sections
    duplicateMatches.size shouldBe 1
    duplicateMatches should contain (Match("dup1", 1))
  }
}
