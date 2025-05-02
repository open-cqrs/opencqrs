{
  inputs.nixpkgs.url = "github:nixos/nixpkgs";
  inputs.flake-utils.url = "github:numtide/flake-utils";

  outputs = { nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachSystem [ "aarch64-darwin" "x86_64-darwin" ] (system:
      let
        pkgs = import nixpkgs { inherit system; };
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [
            pkgs.jdk21
            pkgs.gnupg
            pkgs.python3Packages.mkdocs-material
            pkgs.python3Packages.cairosvg
            pkgs.python3Packages.pillow
            pkgs.python3Packages.mkdocs-macros
#            pkgs.python3Packages.mkdocs-autorefs
#            pkgs.python3Packages.mkdocs-redirects
#            pkgs.python3Packages.mkdocs-awesome-pages-plugin
          ];
        };
      });
}
