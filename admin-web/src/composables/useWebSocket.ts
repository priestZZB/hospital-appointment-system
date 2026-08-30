import { onBeforeUnmount, ref, shallowRef } from 'vue'

interface StompClientLike {
  activate: () => void
  deactivate: () => void
  connected: boolean
  onConnect: ((frame?: unknown) => void) | null
  onWebSocketClose: ((evt: unknown) => void) | null
  onStompError: ((frame: unknown) => void) | null
  subscribe: (destination: string, callback: (message: { body: string }) => void) => { unsubscribe: () => void }
}

/**
 * WebSocket 订阅组合式封装（迭代5 阶段6）
 *
 * 基于 SockJS + STOMP（@stomp/stompjs），统一管理连接生命周期：
 * - 断线自动重连（reconnectDelay=5s）
 * - 重连成功后自动重放订阅（resubscribe）
 * - 页面卸载自动断开
 *
 * 用法：
 *   const { subscribe } = useWebSocket()
 *   subscribe('/topic/call/1', (body) => { ... })
 *
 * 说明：简化实现——每次 connect 时建立连接，connected 标志同步 UI；
 * 断线重连由 STOMP 内部处理，重连成功触发 onConnect 时重新订阅全部 topic。
 */
export function useWebSocket() {
  const connected = ref(false)
  const client = shallowRef<StompClientLike | null>(null)
  /** topic → handler 映射（重连后重放） */
  const handlers = new Map<string, (body: unknown) => void>()

  function registerClientHandlers(instance: StompClientLike & Record<string, unknown>) {
    instance.onConnect = () => {
      connected.value = true
      // 重连后重放所有订阅
      handlers.forEach((handler, destination) => {
        try {
          instance.subscribe(destination, (message) => {
            try {
              handler(JSON.parse(message.body))
            } catch {
              handler(message.body)
            }
          })
        } catch {
          // 单个订阅失败不影响其他
        }
      })
    }
    instance.onWebSocketClose = () => {
      connected.value = false
    }
    instance.onStompError = () => {
      connected.value = false
    }
  }

  async function ensureClient(): Promise<StompClientLike | null> {
    if (client.value?.connected) return client.value
    if (client.value) return client.value // 连接中/重连中，等待 onConnect
    try {
      const [{ Client }, SockJSModule] = await Promise.all([
        import('@stomp/stompjs'),
        import('sockjs-client'),
      ])
      const SockJS = (SockJSModule as { default?: unknown }).default ?? SockJSModule
      const instance = new Client({
        webSocketFactory: () => new (SockJS as new (url: string) => WebSocket)('/api/ws') as unknown as WebSocket,
        reconnectDelay: 5000,
        debug: () => undefined,
      })
      registerClientHandlers(instance as unknown as StompClientLike & Record<string, unknown>)
      instance.activate()
      client.value = instance as unknown as StompClientLike
      return client.value
    } catch {
      return null
    }
  }

  /**
   * 订阅 topic；连接未就绪时自动发起连接，重连成功后自动重放。
   * 返回退订函数。
   */
  function subscribe(destination: string, handler: (body: unknown) => void): () => void {
    handlers.set(destination, handler)
    void ensureClient().then((c) => {
      if (c?.connected) {
        try {
          c.subscribe(destination, (message) => {
            try {
              handler(JSON.parse(message.body))
            } catch {
              handler(message.body)
            }
          })
        } catch {
          // 连接建立瞬时竞态：等 onConnect 重放
        }
      }
    })
    return () => {
      handlers.delete(destination)
    }
  }

  function disconnect(): void {
    handlers.clear()
    client.value?.deactivate()
    client.value = null
    connected.value = false
  }

  onBeforeUnmount(disconnect)

  return { connected, subscribe, disconnect }
}