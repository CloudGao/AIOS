# 输入、坐标与观察凭据

## 观察与输入的完整例子

```json
{"operation":"tree","arguments":{"session_id":"实际会话ID"}}
```

读取返回 windows、width、height、coordinate_space 和 observation_id。确定目标后：

```json
{"operation":"tap","arguments":{"session_id":"同一个实际会话ID","observation_id":"刚才返回的原值","x":100,"y":200}}
```

示例中的 ID、凭据和示范坐标 100/200 必须替换为真实观察值；x/y 为整数。新回执前后台都提供 observation_id，同时兼容旧 observation_token；旧会话只返回 observation_token 时，可原样用该字段提交。不得猜测凭据、从另一会话借用，或同时发送互相矛盾的别名。

每次输入都会消费旧观察凭据。下一次输入必须依据新的有效观察：优先使用输入回执附带的 observation；没有有效新观察时才调用 tree/screenshot，再决定动作。status 不产生凭据。只读观察和状态不等于执行成功。

## 坐标

坐标属于本次 observation_id：截图 coordinate_space=image_pixels，width/height 与实际发送图片一致，直接取图像坐标，宿主映射到 display_width/display_height，模型禁止再缩放。树 coordinate_space=display_pixels，直接取 bounds 内部中心；bounds_valid=false、不可见、零面积节点不能作为目标。不混用树与截图凭据、尺寸、坐标；不猜底部按钮高度，超界重新观察。

旋转、分辨率变化、页面变化或恢复后重新观察。screenshot 返回 observation_pending 时，只重读观察；没有拿到图片和凭据前不要输入。

## 输入操作参数

所有输入都携带 session_id 和最新 observation_id（或旧 observation_token）。

| operation | 其他 arguments |
| --- | --- |
| tap / long_press | x、y，整数 |
| double_tap | x、y；interval_ms 可选（40–300，默认120）。这是一次原子双击，不拆成两轮 tap |
| swipe | x1、y1、x2、y2，整数；duration_ms 可选（100–5000） |
| gesture | pointers（1–3个，每个含唯一 id 和2–32个 `[x,y]` 路径点）、duration_ms（100–5000）；hold_ms 可选（0–3000）。用于持续按住拖动、双指缩放/旋转和三指轨迹 |
| text | text；replace 可选，默认追加。前台需先聚焦非密码输入框 |
| back | 无 |

create、launch、pause、resume、handover、close 是会话操作；不可拿它们绕过用户接管。create 需 package/mode；其余需 session_id。后台 session limit、应用占用、权限交接要按实际状态处理。

普通滚动或翻页使用 swipe；必须保持同一次接触的排序拖动使用单指 gesture；缩放或旋转使用两条同时开始和结束的路径。系统返回优先 back，应用侧栏才从内容边缘向内 swipe。Home、最近任务和通知栏当前没有此工具的授权语义，不用坐标手势冒充。未知字段、重复 pointer id、越界点和不支持的时长会在输入前拒绝，不能改名猜参数。

