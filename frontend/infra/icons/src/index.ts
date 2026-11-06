import { defineComponent, h, type PropType } from "vue";
import User from "~icons/ri/user-3-line";
import Lock from "~icons/ri/lock-2-line";
import Sun from "~icons/ri/sun-line";
import Moon from "~icons/ri/moon-line";
import Monitor from "~icons/ri/computer-line";
import Globe from "~icons/ri/global-line";
import ArrowRight from "~icons/ri/arrow-right-line";
import Shield from "~icons/ri/shield-check-line";
import Home from "~icons/ri/home-4-line";
import Settings from "~icons/ri/settings-3-line";
import Search from "~icons/ri/search-line";
import Logout from "~icons/ri/logout-circle-r-line";
import Organization from "~icons/ri/organization-chart";
import Refresh from "~icons/ri/refresh-line";
import Density from "~icons/ri/line-height";
import Fullscreen from "~icons/ri/fullscreen-line";
import FullscreenExit from "~icons/ri/fullscreen-exit-line";
import ArrowUp from "~icons/ri/arrow-up-s-line";
import ArrowDown from "~icons/ri/arrow-down-s-line";
import Pin from "~icons/ri/pushpin-line";
import Drag from "~icons/ri/draggable";
import Add from "~icons/ri/add-line";

/** 平台语义图标映射；统一采用 Remix Icon 线性图标，由构建插件按需生成本地 SVG，不请求外部图标服务。 */
export const platformIcons = {
  user: User, lock: Lock, sun: Sun, moon: Moon, monitor: Monitor,
  globe: Globe, arrowRight: ArrowRight, shield: Shield, home: Home,
  settings: Settings, search: Search, logout: Logout, organization: Organization,
  refresh: Refresh, density: Density, fullscreen: Fullscreen, fullscreenExit: FullscreenExit,
  arrowUp: ArrowUp, arrowDown: ArrowDown, pin: Pin, drag: Drag, add: Add
} as const;

/** 平台图标名称；业务模块使用语义名称，不依赖后台模板内部的 ReIcon 实现。 */
export type PlatformIconName = keyof typeof platformIcons;

/**
 * 平台共用图标组件，可直接用于页面、按钮及输入框插槽。
 * name 为语义图标名；size 为像素尺寸，默认 20；label 为可选的无障碍描述。
 * 装饰图标不设置 label，自动对辅助技术隐藏；独立表达含义的图标应提供中文 label。
 */
export const AppIcon = defineComponent({
  name: "AppIcon",
  inheritAttrs: false,
  props: {
    name: { type: String as PropType<PlatformIconName>, required: true },
    size: { type: Number, default: 20 },
    label: { type: String, default: "" }
  },
  setup(props, { attrs }) {
    return () => h(platformIcons[props.name], {
      ...attrs, width: props.size, height: props.size,
      "aria-hidden": props.label ? undefined : "true",
      "aria-label": props.label || undefined,
      role: props.label ? "img" : undefined,
      style: [{ flexShrink: 0, verticalAlign: "middle" }, attrs.style]
    });
  }
});
