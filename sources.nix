{
  pkgs = import (builtins.fetchTarball {
    url = "https://github.com/NixOS/nixpkgs/archive/e065200fc90175a8f6e50e76ef10a48786126e1c.tar.gz";
  }) {};
}
