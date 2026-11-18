type Listener = (payload: unknown) => void;

const globalForEvents = globalThis as typeof globalThis & {
  __floorcastListeners?: Set<Listener>;
};

const listeners = globalForEvents.__floorcastListeners ??= new Set<Listener>();

export function publish(payload: unknown) {
  for (const listener of listeners) {
    try {
      listener(payload);
    } catch {
      // ignore broken subscribers
    }
  }
}

export function subscribe(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
