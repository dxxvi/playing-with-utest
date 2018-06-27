import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class WebsocketService {
  private ws: WebSocket;
  private name2Subject: Map<string, Subject<any>> = new Map<string, Subject<any>>();

  constructor() {
    const url = new URL(location.href);
    this.ws = new WebSocket(`ws://${url.hostname}:${url.port}/ws`);
    this.ws.onmessage = (event) => {
      const message: string = event.data;
      const i = message.indexOf(': ');
      if (i === -1) {
        console.error(`Unknown message ${message}`);
      }
      else {
        const symbol = message.substring(0, i);
        this.sendMessageThroughSubject('SYMBOL_FOUND', symbol);
        this.sendMessageThroughSubject(symbol, `${message.substr(i + 2)}`);
      }
    };
    this.ws.onerror   = (event) => {
      console.log(`websocket error ${event}`);
    };
    this.ws.onclose   = (event) => {
      console.log('websocket closes');
    };
  }

  sendMessageThroughWebsocket(message: string) {
    this.ws.send(message);
  }

  getSubject(name: string): Subject<any> {
    name = name.trim().toUpperCase();
    let subject = this.name2Subject.get(name);
    if (subject === undefined) {
      subject = new Subject<any>();
      this.name2Subject.set(name, subject);
    }
    return subject;
  }

  sendMessageThroughSubject(name: string, message: string) {
    this.getSubject(name).next({ text: message });
  }
}
