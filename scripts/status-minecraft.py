#!/usr/bin/env python3
"""Read Minecraft 1.7 status without joining or authenticating."""
import json
import socket
import struct
import sys


def varint(n):
    out = bytearray()
    while True:
        b = n & 127
        n >>= 7
        out.append(b | (128 if n else 0))
        if not n:
            return bytes(out)


def readvar(sock):
    n = 0
    for i in range(5):
        b = sock.recv(1)
        if not b:
            raise EOFError("status connection closed")
        n |= (b[0] & 127) << (i * 7)
        if not b[0] & 128:
            return n
    raise ValueError("invalid VarInt")


def exact(sock, n):
    out = bytearray()
    while len(out) < n:
        chunk = sock.recv(n - len(out))
        if not chunk:
            raise EOFError("truncated status")
        out.extend(chunk)
    return bytes(out)


def query(host, port):
    with socket.create_connection((host, port), timeout=10) as sock:
        name = host.encode()
        handshake = b"\x00" + varint(5) + varint(len(name)) + name + struct.pack(">H", port) + b"\x01"
        sock.sendall(varint(len(handshake)) + handshake + b"\x01\x00")
        length = readvar(sock)
        if length > 1048576:
            raise ValueError("oversized status")
        if readvar(sock) != 0:
            raise ValueError("unexpected packet")
        data = json.loads(exact(sock, readvar(sock)))
        mods = data.get("modinfo", {}).get("modList", [])
        return {"host": host, "port": port, "version": data.get("version"),
            "players_online": data.get("players", {}).get("online"),
            "feature_mods": [m for m in mods if m.get("modid") in ("ae2conveniencefeatures", "ae2_auto_pattern_upload")],
            "reported_mod_count": len(mods)}


if __name__ == "__main__":
    print(json.dumps(query(sys.argv[1], int(sys.argv[2])), ensure_ascii=False, indent=2))
