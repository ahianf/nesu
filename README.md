# nesu

A very simple NES emulator written in Java.

## Run

Requires Java 21 and Maven.

```sh
mvn compile exec:java -Dexec.mainClass=cl.ahianf.Main -Dexec.args="/path/to/game.nes"
```

Options:

```sh
--scale 3
--width 768
--height 720
--conf keybindings.conf
```

## Controls

Player 1:

| NES | Key |
| --- | --- |
| A | J |
| B | K |
| Select | Right Shift |
| Start | Enter |
| Up | W |
| Down | S |
| Left | A |
| Right | D |

Player 2:

| NES | Key |
| --- | --- |
| A | Numpad 5 |
| B | Numpad 6 |
| Select | Numpad 8 |
| Start | Numpad 9 |
| Up | Up |
| Down | Down |
| Left | Left |
| Right | Right |

Emulator:

| Key | Action |
| --- | --- |
| F2 | Pause / unpause |
| F3 | Step one frame while paused |

## Keybindings

By default, nesu reads `keybindings.conf` if it exists. Use `--conf` to choose another file.

```ini
[Player1]
A=J
B=K
Select=RShift
Start=Return
Up=W
Down=S
Left=A
Right=D
```

## Scope

This is a small Java emulator, not a cycle-perfect NES implementation. It supports a limited set of common mappers and is intended for simple ROM compatibility, experimentation, and learning.
