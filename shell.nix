{ sources ? import ./sources.nix
, pkgs ? sources.pkgs
, jdk ? pkgs.openjdk11
, buildEnv ? pkgs.buildEnv
, buildSelf ? false }:

let
  inherit (pkgs) callPackage mkShell;
  inherit (pkgs.lib) optionals;

  thisPackage = callPackage ./. {inherit pkgs jdk;};
  thisPackageDeps =
    thisPackage.buildInputs ++
    thisPackage.propagatedBuildInputs ++
    thisPackage.nativeBuildInputs ++
    thisPackage.propagatedNativeBuildInputs;

  envWithEverything = buildEnv {
    name = "ibck-and-deps";
    paths = thisPackageDeps;
  };

in mkShell {
  buildInputs = if buildSelf then
    [thisPackage] ++ [envWithEverything]
  else
    thisPackageDeps ++ [envWithEverything];

  # Create a link called `nix-jdk` that points to the JDK that was used.
  # This allows us to use IntelliJ IDEA without worrying about breaking our project
  # if we upgrade nixpkgs by specifying ./nix-jdk as the location of the JDK
  shellHook = ''
    rm -f ./nix-jdk
    ln -s "${envWithEverything}" ./nix-jdk
  '';
}
