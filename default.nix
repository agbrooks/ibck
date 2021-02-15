{
  sources ? import ./sources.nix,
  pkgs ? sources.pkgs,
  jdk ? pkgs.openjdk11,
  scala_2_13 ? pkgs.scala_2_13
}:

let
  ibkrApi = pkgs.callPackage ./ibkr-api.nix { inherit jdk; };
in pkgs.stdenv.mkDerivation rec {
  pname = "ibck";
  version = "1.0.0";
  src = ./.;
  nativeBuildInputs = [ibkrApi jdk scala_2_13];
}
