import {Component, ElementRef, OnDestroy, OnInit} from '@angular/core';
import {WebsocketService} from './websocket.service';
import {Subscription} from 'rxjs';

@Component({
  selector: 'div.configuration',
  templateUrl: './configuration.component.html',
  styleUrls: ['./configuration.component.css']
})
export class ConfigurationComponent implements OnInit, OnDestroy {
  open = false;
  message: string = null;
  symbol: string = null;
  fundamental: {
    open: number,
    low: number,
    high: number,
    high52weeks: number,
    low52weeks: number,
    dividendYield: number,
    description: string,
    yearFound: number,
    symbol: string
  } = null;
  private subscription: Subscription;

  constructor(private el: ElementRef, private websocketService: WebsocketService) { }

  ngOnInit() {
    this.subscription = this.websocketService.getSubject('FUNDAMENTAL_REVIEW').asObservable().subscribe(message => {
      const i = message.text.indexOf(': ');
      if (i == -1) {
        console.error(`Unknown message ${message.text}`);
      }
      else {
        this.fundamental = JSON.parse(message.text.substr(i + 2));
        this.fundamental.symbol = message.text.substring(0, i);
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  clickConfiguration() {
    this.open = !this.open;
    this.el.nativeElement.style.position = this.open ? 'static' : 'fixed';
  }

  getFundamental() {
    if (this.symbol != null && this.symbol.trim() != '') {
      this.message = `FUNDAMENTAL_REVIEW: ${this.symbol}`;
      this.sendMessageToServer();
      setTimeout(() => {
        this.symbol = null;
      }, 3456);
    }
  }

  sendMessageToBrowser() {
    if (this.message != null && this.message.trim() != '') {
      this.websocketService.processReceivedString(this.message);
      setTimeout(() => {
        this.message = null;
      }, 3456)
    }
  }

  sendMessageToServer() {
    if (this.message != null && this.message.trim() != '') {
      this.websocketService.sendMessageThroughWebsocket(this.message);
      setTimeout(() => {
        this.message = null;
      }, 3456)
    }
  }

  setDebug(debug: boolean) {
    if (this.symbol != null && this.symbol.trim() != '') {
      this.message = `${debug ? 'DEBUG_ON:' : 'DEBUG_OFF:'} ${this.symbol}`;
      this.sendMessageToServer();
      setTimeout(() => {
        this.symbol = null;
      }, 3456);
    }
  }
}
