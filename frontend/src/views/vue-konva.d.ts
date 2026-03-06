/**
 * Vue-Konva 全局组件类型声明
 *
 * VueKonva 通过 app.use(VueKonva) 在运行时注册，但 Volar（Vue 语言服务器）
 * 无法自动感知动态注册的组件，会将 <v-stage> 等标签报告为"Unknown HTML tag"。
 * 在此补充 GlobalComponents 类型声明以消除 IDE 警告。
 */
import type { DefineComponent } from 'vue'

declare module '@vue/runtime-core' {
    export interface GlobalComponents {
        VStage: DefineComponent<any, any, any>
        VLayer: DefineComponent<any, any, any>
        VRect: DefineComponent<any, any, any>
        VCircle: DefineComponent<any, any, any>
        VEllipse: DefineComponent<any, any, any>
        VWedge: DefineComponent<any, any, any>
        VLine: DefineComponent<any, any, any>
        VArrow: DefineComponent<any, any, any>
        VText: DefineComponent<any, any, any>
        VGroup: DefineComponent<any, any, any>
        VImage: DefineComponent<any, any, any>
        VTransformer: DefineComponent<any, any, any>
        VRing: DefineComponent<any, any, any>
        VArc: DefineComponent<any, any, any>
        VStar: DefineComponent<any, any, any>
        VLabel: DefineComponent<any, any, any>
        VTag: DefineComponent<any, any, any>
        VRegularPolygon: DefineComponent<any, any, any>
        VPath: DefineComponent<any, any, any>
    }
}

export {}
