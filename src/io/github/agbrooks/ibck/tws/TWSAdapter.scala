package io.github.agbrooks.ibck.tws

/**
 * A TWSAdapter mediates all interaction with IBKR TWS at a higher level than the EWrapper / EClient interfaces
 * exposed by the TWS API.
 *
 * The EWrapper interface is absolutely enormous (and frankly, gross), so I've split it up into several mixin traits
 * with names ending in "Feature". These abstract away low-level messaging details to allow you to "just ask questions"
 * about the data and ignore TWS' many quirks.
 *
 * For example, assuming that you've instantiated and connected a TWSAdapter, you could find all underlyings
 * on which you have open short puts by running something like:
 *
 *  adapter
 *    .getPositions(accountNumber)
 *    .filter(pos => pos.isShort && pos.isPut)
 *    .map(_.contract.symbol())
 *    .toSet
 *
 * (There are quite a few fiddly things you have to do with the IBKR-supplied TWS API to pull this off. If you're
 * curious, the PositionsFeature / MessageCombiner manage most of it.)
 *
 * The TWSAdapter is designed to be thread-safe.
 *
 * @param host host where TWS or IBKR gateway is running
 * @param port port where TWS or IBKR gateway is running
 * @param clientId A unique client ID that identifies this application to TWS
 */
class TWSAdapter(host: String = "localhost", port: Int = 7496, clientId: Int = 0)
  extends BaseTWSAdapter(host, port, clientId)
  with AccountSummaryFeature
  with PositionsFeature
  // TODO: Open orders (easy, just use MessageCombiner again)
  // TODO: Option Quotes / Greeks (the 'tick' API) : Make 'oneshot' case for MessageCombiner?
  // TODO: 'normal' quotes : Can snapshot (use MessageCombiner) or maybe make a better pub/sub-focused abstraction?