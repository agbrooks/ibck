# ibck

Portfolio sanity-checker for [IBKR](https://interactivebrokers.com) accounts (name is a pun on `fsck`).

So far, it's fairly primitive/hacky, and is focused only on writing equity options. However, it does present a nice proof-of-concept for a high-level wrapper to the IBKR TWS API that ought to be a huge breath of fresh air (example below).

# Sanity checks

Right now, it has only two basic sanity checks:

* All short calls in each account should be covered.

* Each account should have enough cash to cover the exercise of all short puts at any time should any/all underlyings abruptly move to any price.

I'm hoping to add more when I have the time to wrap more of the API.

### Isn't that second condition awfully conservative?

Oh, yes. Here's the thing: I don't trust any value-at-risk analysis (like those that TWS will happily show you) because I don't want to assume a distribution for the price movement of any underlying. _Weird stuff happens, and I want to sleep at night._

# TWS API Wrapper

The interesting part is mostly visible in the [`TWSAdapter`](src/io/github/agbrooks/ibck/tws/TWSAdapter.scala) API, which lets you "just ask questions" about your portfolios without getting bogged down in all the TWS messaging minutia.

For example, to find the set of all symbols of all underlyings on which you have open short puts, the `TWSAdapter` wrapper I've written allows you to just say something like:
```
adapter
  .getPositions(accountNumber)
  .filter(pos => pos.isShort && pos.isPut)
  .map(_.contract.symbol())
  .toSet
```

which is substantially easier than using the IBKR TWS API "directly".

# What's broken / needs work

* Most exceptions are placeholders.

* There are a lot of unwrapped parts of the IBKR `EWrapper` API.

* The notion of a "sanity check" should probably be abstracted away to allow us to factor out some of the spaghetti in `Main` and make it easy to extend the set of available sanity checks.

* There's no `build.sbt` (sorry).

* There's no way to specify the host/port of TWS / the IBKR gateway from the command line; it's assumed to be the default.

# Building / running this

I'm assuming you have [`nix`](https://nixos.org) installed.

Running `nix-shell` will install `scala`, a `jdk`, and the IBKR API under `./nix-jdk`. You'll want to tell IntelliJ to look there for the JDK, `scalac`, all `jar`s, etc.

One day, I'll get around to writing a `build.sbt` so you can realistically do this without an IDE.
