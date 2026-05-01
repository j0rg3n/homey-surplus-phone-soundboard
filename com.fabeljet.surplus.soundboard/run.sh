#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
homey app run --remote 2>&1 | ts '[%H:%M:%S]'
