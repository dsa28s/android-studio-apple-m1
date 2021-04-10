#!/bin/sh

clang -arch arm64 -mmacosx-version-min=10.8 -framework AppKit -lobjc -o restarter restarter.m
