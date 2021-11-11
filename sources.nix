{
  pkgs = import (builtins.fetchTarball {
    url = "https://github.com/NixOS/nixpkgs/archive/e74894146a42ba552ebafa19ab2d1df7ccbc1738.tar.gz";
  }) {};
}
