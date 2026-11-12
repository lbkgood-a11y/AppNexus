// 这里存放本地图标，在 src/layout/index.vue 文件中加载，避免在首启动加载
import { getSvgInfo } from "@pureadmin/utils";
import { addIcon } from "@iconify/vue/dist/offline";

// https://icon-sets.iconify.design/ep/?keyword=ep
import EpMenu from "~icons/ep/menu?raw";
import EpEdit from "~icons/ep/edit?raw";
import EpGuide from "~icons/ep/guide?raw";
import EpSetUp from "~icons/ep/set-up?raw";
import EpMonitor from "~icons/ep/monitor?raw";
import EpLollipop from "~icons/ep/lollipop?raw";
import EpHistogram from "~icons/ep/histogram?raw";
import EpHomeFilled from "~icons/ep/home-filled?raw";

// https://icon-sets.iconify.design/ri/?keyword=ri
import RiMindMap from "~icons/ri/mind-map?raw";
import RiAdminFill from "~icons/ri/admin-fill?raw";
import RiTableLine from "~icons/ri/table-line?raw";
import RiLinksFill from "~icons/ri/links-fill?raw";
import RiAdminLine from "~icons/ri/admin-line?raw";
import RiListCheck from "~icons/ri/list-check?raw";
import RiSearchLine from "~icons/ri/search-line?raw";
import RiWindowLine from "~icons/ri/window-line?raw";
import RiUbuntuFill from "~icons/ri/ubuntu-fill?raw";
import RiHistoryFill from "~icons/ri/history-fill?raw";
import RiEditBoxLine from "~icons/ri/edit-box-line?raw";
import RiCodeBoxLine from "~icons/ri/code-box-line?raw";
import RiArtboardLine from "~icons/ri/artboard-line?raw";
import RiMarkdownLine from "~icons/ri/markdown-line?raw";
import RiFileInfoLine from "~icons/ri/file-info-line?raw";
import RiFileListLine from "~icons/ri/file-list-line?raw";
import RiBankCardLine from "~icons/ri/bank-card-line?raw";
import RiFilePpt2Line from "~icons/ri/file-ppt-2-line?raw";
import RiGitBranchLine from "~icons/ri/git-branch-line?raw";
import RiSettings3Line from "~icons/ri/settings-3-line?raw";
import RiOrganizationChart from "~icons/ri/organization-chart?raw";
import RiUserVoiceLine from "~icons/ri/user-voice-line?raw";
import RiBookmark2Line from "~icons/ri/bookmark-2-line?raw";
import RiFileSearchLine from "~icons/ri/file-search-line?raw";
import RiChatSearchLine from "~icons/ri/chat-search-line?raw";
import RiInformationLine from "~icons/ri/information-line?raw";
import RiTerminalWindowLine from "~icons/ri/terminal-window-line?raw";
import RiCheckboxCircleLine from "~icons/ri/checkbox-circle-line?raw";
import RiBarChartHorizontalLine from "~icons/ri/bar-chart-horizontal-line?raw";
import RiBook2Line from "~icons/ri/book-2-line?raw";
import RiBuildingLine from "~icons/ri/building-line?raw";
import RiGroupLine from "~icons/ri/group-line?raw";
import RiHomeSmileLine from "~icons/ri/home-smile-line?raw";
import RiMenuLine from "~icons/ri/menu-line?raw";
import RiQuestionLine from "~icons/ri/question-line?raw";
import RiServerLine from "~icons/ri/server-line?raw";
import RiSettings4Line from "~icons/ri/settings-4-line?raw";
import RiShieldKeyholeLine from "~icons/ri/shield-keyhole-line?raw";
import RiShieldUserLine from "~icons/ri/shield-user-line?raw";
import RiUserSettingsLine from "~icons/ri/user-settings-line?raw";

import RiFlowChart from "~icons/ri/flow-chart?raw";
import RiPrinterLine from "~icons/ri/printer-line?raw";

const icons = [
  // 单据目录及工作台采用已登记的本地 SVG，不依赖运行时远程加载。
  ["ri/file-list-line", RiFileListLine],
  // 流程与打印菜单使用本地 SVG，避免配置了名称却未注册而显示空白。
  ["ri/flow-chart", RiFlowChart],
  ["ri/printer-line", RiPrinterLine],
  // Element Plus Icon: https://github.com/element-plus/element-plus-icons
  ["ep/menu", EpMenu],
  ["ep/edit", EpEdit],
  ["ep/guide", EpGuide],
  ["ep/set-up", EpSetUp],
  ["ep/monitor", EpMonitor],
  ["ep/lollipop", EpLollipop],
  ["ep/histogram", EpHistogram],
  ["ep/home-filled", EpHomeFilled],
  // Remix Icon: https://github.com/Remix-Design/RemixIcon
  ["ri/mind-map", RiMindMap],
  ["ri/admin-fill", RiAdminFill],
  ["ri/table-line", RiTableLine],
  ["ri/links-fill", RiLinksFill],
  ["ri/admin-line", RiAdminLine],
  ["ri/list-check", RiListCheck],
  ["ri/search-line", RiSearchLine],
  ["ri/window-line", RiWindowLine],
  ["ri/ubuntu-fill", RiUbuntuFill],
  ["ri/history-fill", RiHistoryFill],
  ["ri/edit-box-line", RiEditBoxLine],
  ["ri/code-box-line", RiCodeBoxLine],
  ["ri/artboard-line", RiArtboardLine],
  ["ri/markdown-line", RiMarkdownLine],
  ["ri/file-info-line", RiFileInfoLine],
  ["ri/bank-card-line", RiBankCardLine],
  ["ri/file-ppt-2-line", RiFilePpt2Line],
  ["ri/git-branch-line", RiGitBranchLine],
  ["ri/settings-3-line", RiSettings3Line],
  ["ri/organization-chart", RiOrganizationChart],
  ["ri/user-voice-line", RiUserVoiceLine],
  ["ri/bookmark-2-line", RiBookmark2Line],
  ["ri/file-search-line", RiFileSearchLine],
  ["ri/chat-search-line", RiChatSearchLine],
  ["ri/information-line", RiInformationLine],
  ["ri/terminal-window-line", RiTerminalWindowLine],
  ["ri/checkbox-circle-line", RiCheckboxCircleLine],
  ["ri/bar-chart-horizontal-line", RiBarChartHorizontalLine],
  ["ri/book-2-line", RiBook2Line],
  ["ri/building-line", RiBuildingLine],
  ["ri/group-line", RiGroupLine],
  ["ri/home-smile-line", RiHomeSmileLine],
  ["ri/menu-line", RiMenuLine],
  ["ri/question-line", RiQuestionLine],
  ["ri/server-line", RiServerLine],
  ["ri/settings-4-line", RiSettings4Line],
  ["ri/shield-keyhole-line", RiShieldKeyholeLine],
  ["ri/shield-user-line", RiShieldUserLine],
  ["ri/user-settings-line", RiUserSettingsLine]
];

// 本地菜单图标，后端在路由的 icon 中返回对应的图标字符串并且前端在此处使用 addIcon 添加即可渲染菜单图标
icons.forEach(([name, icon]) => {
  addIcon(name as string, getSvgInfo(icon as string));
});
