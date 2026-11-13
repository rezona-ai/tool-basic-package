# ui/saturn/
> L2 | 父级: ../CLAUDE.md

成员清单
SaturnScene.kt: SaturnBackdrop，AppNav 底层的常驻舞台；按档位选择着色器或 Canvas 版，镜头平滑推到当前申请，驱动帧时钟、粒子、暗角，并把整帧录进离屏 GraphicsLayer（只渲染一次，屏幕与玻璃面板模糊共用）
SaturnDirector.kt: 导演，页面用 SaturnStage 申请镜头（最后申请者生效、离开自动交还），Modifier.saturnTilt 让手指转动视角
SaturnCamera.kt: 几何真相源，SaturnCamera（环平面 → 屏幕投影）、SaturnView（设计单位的镜头目标，placed 适配不同屏高）、SaturnShots（原型四个镜头）、SaturnGeom 常量
SaturnShader.kt: 高档位 AGSL 片元着色器，深空背景、扁球土星、光环与互投阴影、用量轨道、扫描光束、冲击波；整帧常量（旋转、相机、光源）在 CPU 算好传入，背景只在星球外计算
SaturnFallback.kt: 低档位 Canvas 版土星，按后半圈环 → 星球 → 前半圈环三层绘制，Path 逐帧复用
Particles.kt: 环平面粒子系统，ORBIT 首页星尘环绕 / INFLOW 扫描吸入 / SPIRAL 清理螺旋 / BURST 完成迸发

档位: API ≥ 33 且非低内存设备走 SaturnShader；否则走 SaturnFallback；着色器编译失败也自动退回 Canvas 版。
系统"移除动画"开启时时间放慢到 0.15 倍，粒子关闭。
性能约定: 改效果时保持"每帧只渲染一次舞台"——不要去掉 backdrop 的离屏策略，也不要把整帧常量写回片元着色器。
一致性: 着色器、降级版、粒子共用 SaturnCamera 的旋转与透视（open = -0.42, roll = -0.38, D = 8）；改任何一处都要同步另外两处。
原型: HTML/WebGL 版逐行对应 https://claude.ai/artifact/D4D7LaCqhaayYoNw8C9v9q

[PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
