#!/usr/bin/env bash

set -euo pipefail

version=""
default_install_dir="$HOME/.local/bin"
install_dir="$default_install_dir"
download_dir=""

print_help() {
  echo "Installs latest (or specific) version of the tool. Installation directory defaults to /usr/local/bin."
  echo
  echo "Usage:"
  echo "install [--dir <dir>] [--download-dir <download-dir>] [--version <version>]"
  echo
  echo "Defaults:"
  echo " * Installation directory: ${default_install_dir}"
  echo " * Download directory: temporary"
  echo " * Version: <Latest release on github>"
  exit 1
}

while [[ $# -gt 0 ]]
do
  key="$1"
  case "$key" in
    --dir)
        install_dir="$2"
        shift
        shift
        ;;
    --download-dir)
        download_dir="$2"
        shift
        shift
        ;;
    --version)
        version="$2"
        shift
        shift
        ;;
    *)    # unknown option
        print_help
        shift
        ;;
  esac
done

if [[ -z "$download_dir" ]]; then
  download_dir="$(mktemp -d)"
  trap 'rm -rf "$download_dir"' EXIT
fi

github_user="kepler16"
repo="kmono"

case "$(uname -s)" in
Linux*)  os=linux;;
Darwin*) os=macos;;
*)       echo "unknown: $(uname -s)"; exit 1;;
esac

case "$(uname -m)" in
x86_64) arch=amd64;;
*)      arch=$(uname -m);;
esac

if [[ "$version" == "" ]]; then
  version=$(curl -s https://api.github.com/repos/$github_user/$repo/releases/latest | jq -r '.tag_name')
fi

filename="$repo-$os-$arch.tar.gz"
download_url="https://github.com/$github_user/$repo/releases/download/$version/$filename"

echo -e "Downloading $download_url to $download_dir"

curl -o "$download_dir/$filename" -sL "$download_url"
tar -zxf "$download_dir/$filename" -C "$download_dir"

if [[ "$download_dir" != "$install_dir" ]]
then
  mkdir -p "$install_dir"
  if [ -f "$install_dir/$repo" ]; then
    echo "Moving $install_dir/kmono to $install_dir/kmono.old"
    mv -f "$install_dir/kmono" "$install_dir/kmono.old"
  fi
  mv -f "$download_dir/kmono" "$install_dir/kmono"
fi

echo "Successfully installed $repo in $install_dir"
