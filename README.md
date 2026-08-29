# CMDCam
https://www.curseforge.com/minecraft/mc-mods/cmdcam

## Setup
https://github.com/CreativeMD/ForgeMods

## Commands

The mental model of the server command is:

```
start                  = play
path | tracking | preset = what kind of shot to play
closeup | shoulder      = which built in preset to use
```

### Unified `start` tree

```
/cam-server start path <scene> <players> [duration] [loop]
/cam-server start tracking <scene> <target> <players> [duration] [distance_scale] [fov] [damping] [pitch_follow]
/cam-server start preset closeup  <target> <players> [duration] [distance] [fov] [damping] [pitch_follow]
/cam-server start preset shoulder <target> <players> [duration] [distance] [fov] [damping] [pitch_follow]
```

| Sub command | Meaning |
| --- | --- |
| `path` | plays a saved path exactly as authored, no entity binding |
| `tracking` | plays a saved path whose control points are bound to an entity (the scene needs a follow target, see below) |
| `preset closeup` | built in front close-up, camera orbits the face |
| `preset shoulder` | built in over-the-shoulder camera |

Examples:

```
/cam-server start path opening @a
/cam-server start tracking boss_intro @e[tag=boss,limit=1] @a 12s 1.5 60 400 0.5
/cam-server start preset closeup  @e[tag=boss,limit=1] @a 5s 1.8 45 250 0.7
/cam-server start preset shoulder @s @a 8s 2.5 75 350 0.35
```

### Optional camera parameters

The parameters are positional and read left to right, so a later one requires every earlier one to be given as well.

| Parameter | Meaning | Range | Default |
| --- | --- | --- | --- |
| `duration` | how long the shot runs, e.g. `5s`, `1m`, `500ms` | `> 0` | scene duration, `8s` for presets |
| `distance` | absolute camera to target distance, always positive (`preset` only) | `0.2 - 64` | preset default |
| `distance_scale` | scales the local X/Z of every control point (`tracking` only) | `0.05 - 20` | `1.0` |
| `fov` | absolute field of view | `10 - 170` | preset default / template zoom |
| `damping` | pose smoothing half life in milliseconds, `0` disables smoothing | `0 - 5000` | preset default |
| `pitch_follow` | how much of the target pitch the camera follows | `0 - 1` | preset default |

Out of range values are rejected with an error, they are never silently clamped.

`damping` uses a half life in milliseconds instead of a per frame factor, so the camera reacts identically at 30, 60 or 144 FPS.

### Preset defaults

| Preset | distance | FOV | damping | pitch follow | height factor |
| --- | --- | --- | --- | --- | --- |
| `closeup` | 1.5 | 50 | 250 ms | 0.65 | 0.78 |
| `shoulder` | 1.8 | 75 | 350 ms | 0.35 | 0.65 |
| `tracking` | - | template zoom | 300 ms | 0.5 | 0.65 |

`distance` scales the authored offset instead of replacing it, so the shoulder camera keeps its sideways offset proportional when you pull it back.

`fov` behaves differently for `tracking`: if it is omitted the zoom animation authored into the path is kept, if it is given every control point uses that value.

### Creating a tracked path

`start tracking` interprets the control points as offsets in the local space of the target, so the scene has to be authored with a follow target:

```
/cam-server create boss_intro
/cam-server boss_intro follow entity @e[tag=boss,limit=1]
/cam-server boss_intro add
/cam-server boss_intro add
/cam-server boss_intro duration 6s
/cam-server start tracking boss_intro @e[tag=boss,limit=1] @a
```

Local axis convention: `X` right, `Y` up, `Z` behind, `-Z` in front of the target.

### Deprecated aliases

These still work but forward to the new implementation and print a deprecation hint:

```
/cam-server play <scene> <players> [duration] [loop]              ->  /cam-server start path ...
/cam-server start <scene> <target> <players>                      ->  /cam-server start tracking ...
/cam-server closeup <target> <players> [duration]                 ->  /cam-server start preset closeup ...
/cam-server shoulder <target> <players> [duration]                ->  /cam-server start preset shoulder ...
```

## 命令（中文）

统一结构：`start` 表示“开始播放”，第二级 `path / tracking / preset` 表示播放类型，第三级 `closeup / shoulder` 表示内置预设。

```
/cam-server start path <场景> <观众> [时长] [循环]
/cam-server start tracking <场景> <目标> <观众> [时长] [距离缩放] [视场角] [阻尼ms] [俯仰跟随]
/cam-server start preset closeup  <目标> <观众> [时长] [距离] [视场角] [阻尼ms] [俯仰跟随]
/cam-server start preset shoulder <目标> <观众> [时长] [距离] [视场角] [阻尼ms] [俯仰跟随]
```

参数为位置参数，必须从左到右依次填写。超出范围会直接报错，不会静默截断。

| 参数 | 含义 | 范围 | 默认值 |
| --- | --- | --- | --- |
| `duration` | 播放时长 | `> 0` | 场景时长，预设为 `8s` |
| `distance` | 摄影机到目标的距离（仅 `preset`） | `0.2 - 64` | 预设默认值 |
| `distance_scale` | 控制点局部 X/Z 的缩放（仅 `tracking`） | `0.05 - 20` | `1.0` |
| `fov` | 绝对视场角 | `10 - 170` | 预设默认 / 模板自身的 zoom |
| `damping` | 姿态平滑半衰期（毫秒），`0` 为不平滑 | `0 - 5000` | 预设默认值 |
| `pitch_follow` | 摄影机跟随目标俯仰角的比例 | `0 - 1` | 预设默认值 |

阻尼使用毫秒半衰期而非逐帧系数，因此 30 / 60 / 144 FPS 下的手感一致。

预设默认值：

| 预设 | 距离 | 视场角 | 阻尼 | 俯仰跟随 | 高度锚点 |
| --- | --- | --- | --- | --- | --- |
| `closeup` | 1.5 | 50 | 250 ms | 0.65 | 0.78 |
| `shoulder` | 1.8 | 75 | 350 ms | 0.35 | 0.65 |
| `tracking` | - | 模板自身 | 300 ms | 0.5 | 0.65 |

`tracking` 要求场景带跟随目标（相对控制点），否则会把世界坐标当成局部偏移：

```
/cam-server create boss_intro
/cam-server boss_intro follow entity @e[tag=boss,limit=1]
/cam-server boss_intro add
/cam-server boss_intro duration 6s
/cam-server start tracking boss_intro @e[tag=boss,limit=1] @a
```

局部坐标约定：`X` 右，`Y` 上，`Z` 后，`-Z` 前。

旧命令 `play` / `start <场景> <目标> <观众>` / `closeup` / `shoulder` 仍可转发到新实现，并提示已弃用。
