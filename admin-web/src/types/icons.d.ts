/**
 * @win-design-next/icons-vue 的图标补充类型声明。
 * 项目内存在本地 ambient 声明会覆盖包自带的类型（dist/types/index.d.ts），
 * 故在此统一补齐全项目实际用到的图标导出，保证 vue-tsc 通过。
 * 仅声明包内真实存在的图标名，不臆造。
 */
declare module '@win-design-next/icons-vue' {
  import type { Component } from 'vue'

  export const ArrowLeft: Component
  export const ArrowRight: Component
  export const BarChart: Component
  export const CaretBottom: Component
  export const Check: Component
  export const CircleCheck: Component
  export const CircleClose: Component
  export const CirclePlus: Component
  export const CircleWarning: Component
  export const Close: Component
  export const Computer: Component
  export const Copy: Component
  export const Date: Component
  export const Delete: Component
  export const Download: Component
  export const Edit: Component
  export const File: Component
  export const Filter: Component
  export const Flow: Component
  export const Fold: Component
  export const Fullscreen: Component
  export const Guide: Component
  export const Hospital: Component
  export const Key: Component
  export const Link: Component
  export const LineChart: Component
  export const List: Component
  export const ListSolid: Component
  export const ListTimeline: Component
  export const Location: Component
  export const Lock: Component
  export const Minus: Component
  export const Picture: Component
  export const Plus: Component
  export const Qrcode: Component
  export const Rank: Component
  export const Refresh: Component
  export const RefreshLeft: Component
  export const Right: Component
  export const Scan: Component
  export const Search: Component
  export const Send: Component
  export const Server: Component
  export const Setting: Component
  export const Shrink: Component
  export const Stamp: Component
  export const Stop: Component
  export const Talk: Component
  export const Telephone: Component
  export const Time: Component
  export const Tool: Component
  export const TurnOff: Component
  export const Unfold: Component
  export const Upload: Component
  export const User: Component
  export const UserGroup: Component
  export const Verify: Component
  export const ViewGridCard: Component
  export const ZoomIn: Component
  export const ZoomOut: Component
}
