import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import {AppComponent} from "./app.component";

@Injectable({
  providedIn: 'root'
})
export class WebsocketService {
  static DOW_STOCKS: Array<string> = [];

  private ws: WebSocket;
  private name2Subject: Map<string, Subject<any>> = new Map<string, Subject<any>>();

  constructor() {
    const url = new URL(location.href);
    this.ws = new WebSocket(`ws://${url.hostname}:${url.port}/ws`);
    this.ws.onmessage = (event) => {
      this.processReceivedString(event.data);
    };
    this.ws.onerror   = (event) => {
      console.log(`websocket error ${event}`);
    };
    this.ws.onclose   = (event) => {
      console.log('websocket closes');
    };
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

  processReceivedString(message: string) {
    const i = message.indexOf(': ');
    if (i === -1) {
      console.error(`Unknown message ${message}`);
    }
    else if (message.includes('FUNDAMENTAL_REVIEW: ')) {
      // the message looks like FUNDAMENTAL_REVIEW: AMD: { fundamental: { ... }, quotes: [...] }
      this.sendMessageThroughSubject('FUNDAMENTAL_REVIEW', message.replace('FUNDAMENTAL_REVIEW: ', ''));
      // the sent message looks like this: AMD: {...}
    }
    else if (message.includes('NOTICE: ')) {
      // the message looks like NOTICE: PRIMARY/DANGER: You should ...
      this.sendMessageThroughSubject('NOTICE_ADD', message.replace('NOTICE: ', ''));
    }
    else if (message.startsWith('DOW_STOCKS: ')) {
      AppComponent.DOW_STOCKS = JSON.parse(message.replace('DOW_STOCKS: ', ''));
      console.log(`AppComponent.DOW_STOCKS: ${AppComponent.DOW_STOCKS.length}`);
    }
    else {
      const symbol = message.substring(0, i);
      const rest   = message.substr(i + 2);
      // rest is like FUNDAMENTAL: {...} or POSITION: {...}
      this.sendMessageThroughSubject('SYMBOL_FOUND', { symbol: symbol, rest: rest });
    }
  }

  sendMessageThroughSubject(name: string, message: any) {
    this.getSubject(name).next(message);
  }

  sendMessageThroughWebsocket(message: string) {
    this.ws.send(message);
  }
}
