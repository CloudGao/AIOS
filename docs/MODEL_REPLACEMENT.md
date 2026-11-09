# 模型替换与实际验收

本次仅处理两项模型替换、四项已知声明核查和相应产物，不增加其他发布目标。

| 项目 | 当前实现 | 权重体积 |
|---|---|---|
| 照片语义检索 | MIT OpenCLIP ViT-B/16 DataComp.XL-s13B-b90K | 图像、文本及词嵌入共300,247,392字节；原MobileCLIP2为299,462,032字节，增加约0.26% |
| 离线语音 | MIT Whisper Small Q5_1 + whisper.cpp v1.9.5 | 190,085,487字节；原SenseVoice ONNX为239,233,841字节，减少约20.5% |

APK只内置语音模型；OpenCLIP是ROM统一检索模块输入。大型权重不进入Git或源码包，下载地址、大小、SHA在dependencies.lock.json，转换源码随仓库提供。Whisper权重虽更小，其压缩率与旧ONNX不同，因此APK压缩包不保证同比缩小。

## 已取得的模型证据

- 同一COCO128图片集、9类有标注英文查询：旧模型参考mAP 0.682344，新模型参考0.675349，新模型使用实际Android预处理和GPU检索为0.675243。这是小样本回归比较，不能代表所有场景；person和bus等单类有下降，dog和car有提升。
- Android图像预处理与PyTorch参考向量：128张最小余弦0.998768，平均0.999833。
- Pixel 10 / Android 17，图像GPU 752/752节点、文本GPU 709/709节点，均为单分区；数值余弦分别0.99995177、0.99999997。词嵌入查表和分词在CPU，神经网络图没有CPU回退。
- 真机128图像推理P50 56.37ms、P95 68.52ms；文本单例热运行P50 47.39ms、P95 65.01ms。
- Whisper原生ARM64实际识别：中文5.592秒样本得到“开放时间早上九点至下午五点。”，约7.1秒解码；英文7.152秒样本得到完整预期句子，约8.2秒。同一原生解码实例连续两次英文推理也成功。电脑端约2.2至2.5秒。
- 应用内JNI与RecognitionService：在解锁亮屏条件下，中英文连续两次请求均得到预期文本，约8.348秒与8.704秒；中文支持检查和不支持语言错误也通过。锁屏状态的测试曾超时，不宣称后台/锁屏连续识别已验收。临时屏幕设置已恢复。
- 日语、韩语和粤语尚未获得本次质量比较数据，不宣称相对SenseVoice等质。粤语入口映射Whisper的中文识别。

## 构建与材料

APK构建及上述语音实测已通过；ROM模块构建状态见BUILD_ACCEPTANCE.md。历史SenseVoiceService组件名保留用于升级兼容，实际加载的是Whisper。旧模型文件和旧索引保留在用户私有目录，当前新装配不携带旧权重。

新模型来源：android/core-services/third_party/models/provenance.json、licenses/whisper-provenance.json。Whisper源码固定commit、许可证和构建入口位于third_party/whisper/AIOS-PROVENANCE.json、tools/distribution/build-whisper.py。

四项原始声明：eastasianwidth@0.2.0已找到并保存上游完整MIT文件及明确覆盖旧版本的说明。debugserver-types@0.5.0、@npmcli/agent@3.0.0、err-code@2.0.3仍只有已保存的许可声明/作者等原始事实，未取得独立完整原始notice；不伪造版权年份或把标准模板称作上游原件。详见licenses/original-notice-review.json。
