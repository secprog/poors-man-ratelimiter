/**
 * WebSocket utility for real-time analytics updates
 */

class AnalyticsWebSocket {
  constructor(url = 'ws://localhost:8080/api/ws/analytics') {
    this.url = url;
    this.ws = null;
    this.listeners = [];
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 10;
    this.reconnectDelay = 1000;
    this.messageQueue = [];
    this.isConnecting = false;
  }

  connect() {
    if (this.ws || this.isConnecting) return;
    
    this.isConnecting = true;
    
    try {
      this.ws = new WebSocket(this.url);
      
      this.ws.onopen = () => {
        console.log('WebSocket connected');
        this.isConnecting = false;
        this.reconnectAttempts = 0;
        
        // Flush any queued messages
        while (this.messageQueue.length > 0) {
          const msg = this.messageQueue.shift();
          this.send(msg);
        }
      };
      
      this.ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          this.notifyListeners(data);
        } catch (error) {
          console.error('Failed to parse WebSocket message', error);
        }
      };
      
      this.ws.onerror = (error) => {
        console.error('WebSocket error', error);
        this.handleError();
      };
      
      this.ws.onclose = () => {
        console.log('WebSocket closed');
        this.ws = null;
        this.isConnecting = false;
        this.attemptReconnect();
      };
    } catch (error) {
      console.error('Failed to create WebSocket', error);
      this.isConnecting = false;
      this.handleError();
    }
  }

  subscribe(listener) {
    this.listeners.push(listener);
    
    // Auto-connect on first listener
    if (this.listeners.length === 1) {
      this.connect();
    }
    
    // Return unsubscribe function
    return () => {
      this.listeners = this.listeners.filter(l => l !== listener);
      
      // Disconnect if no listeners
      if (this.listeners.length === 0) {
        this.disconnect();
      }
    };
  }

  notifyListeners(data) {
    this.listeners.forEach(listener => {
      try {
        listener(data);
      } catch (error) {
        console.error('Listener error', error);
      }
    });
  }

  send(message) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(typeof message === 'string' ? message : JSON.stringify(message));
    } else {
      // Queue message if not connected
      this.messageQueue.push(message);
      this.connect();
    }
  }

  disconnect() {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  handleError() {
    this.attemptReconnect();
  }

  attemptReconnect() {
    if (this.reconnectAttempts < this.maxReconnectAttempts && this.listeners.length > 0) {
      this.reconnectAttempts++;
      const delay = this.reconnectDelay * Math.pow(2, Math.min(this.reconnectAttempts - 1, 4));
      console.log(`Attempting to reconnect (${this.reconnectAttempts}/${this.maxReconnectAttempts}) in ${delay}ms`);
      
      setTimeout(() => {
        if (this.listeners.length > 0) {
          this.connect();
        }
      }, delay);
    }
  }

  isConnected() {
    return this.ws && this.ws.readyState === WebSocket.OPEN;
  }
}

// Create singleton instance
const analyticsWs = new AnalyticsWebSocket(
  `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/api/ws/analytics`
);

export default analyticsWs;
