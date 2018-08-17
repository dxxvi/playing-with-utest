package home.sparkjava.message

import home.sparkjava.model.DailyQuote

/**
  * @param quotes sorted from the past to yesterday
  */
case class DailyQuoteReturn(quotes: List[DailyQuote])
