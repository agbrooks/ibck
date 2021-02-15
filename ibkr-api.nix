{
  sources ? import ./sources.nix,
  pkgs ? sources.pkgs,
  jdk ? pkgs.openjdk11,
  useDebug ? true
}:

let
  inherit (pkgs) fetchzip stdenv;
  version = "976.01";
  allIbkrSrcs = pkgs.fetchzip {
    url = "http://interactivebrokers.github.io/downloads/twsapi_macunix.${version}.zip";
    sha256 = "0i727dqsd8m9qbgra7xizk7y5qapx6s3i8zs7rnpa138737lryck";
    stripRoot = false;
  };
  jarFile = if useDebug then "TwsApi_debug.jar" else "TwsApi.jar";

in stdenv.mkDerivation {
  pname = "ibkr-io.github.agbrooks.ibck.tws-api";
  inherit version;
  src = "${allIbkrSrcs}/IBJts/source/JavaClient";
  nativeBuildInputs = [ jdk ];
  # JDKs have a hook that will check in share/java for all dependencies of
  # a package and build up a CLASSPATH
  installPhase = ''
    runHook preInstall
    mkdir -p $out/share/java
    cp "${jarFile}" $out/share/java/
    runHook postInstall
  '';
}
