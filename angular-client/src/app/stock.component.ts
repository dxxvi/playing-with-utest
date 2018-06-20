import {Component, ElementRef, Input, OnDestroy, OnInit} from '@angular/core';
import {WebsocketService} from './websocket.service';
import {Subscription} from 'rxjs';
import {Order} from './order';

@Component({
  selector: 'li.stock-panel',
  templateUrl: './stock.component.html',
  styleUrls: ['./stock.component.css']
})
export class StockComponent implements OnInit, OnDestroy {
  symbol: string;
  lastTradePrice: number;
  updatedAt: string;
  open: number = 0;
  low: number = 0;
  high: number = 0;
  quantity: number = 0;
  orders: Array<Order> = [];
  private subscription: Subscription;

  @Input('_symbol') set _symbol(value: string) {
    this.symbol = value.trim().toUpperCase();
  }

  constructor(private websocketService: WebsocketService, private el: ElementRef) {
  }

  ngOnInit() {
    this.subscription = this.websocketService.getSubject(this.symbol).asObservable().subscribe(message => {
      // console.log(`Component ${this.symbol} got a message ${message.text}`);
      const i = message.text.indexOf(': ');
      if (i === -1) {
        console.error(`Unknown message ${message.text}`);
      }
      else {
        const type = message.text.substring(0, i);
        const x = JSON.parse(message.text.substr(i + 2));
        if (type === 'QUOTE') {
          this.lastTradePrice = x.lastTradePrice;
          this.updatedAt = x.updatedAt;
        }
        else if (type === 'POSITION') {
          this.quantity = x.quantity;
        }
        else if (type === 'FUNDAMENTAL') {
          this.open = x.open;
          this.low = x.low;
          this.high = x.high;
        }
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  formatMoney(value: number): string {
    if (Number.isNaN(value)) {
      return '&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;'; // 7 spaces
    }
    const a: number = Math.floor(value);
    const b: number = Math.round(value * 100) - a*100;
    const stringA: string = (a < 10 ? '&nbsp;&nbsp;&nbsp;' : (a < 100 ? '&nbsp;&nbsp;' : (a < 1000 ? '&nbsp;' : ''))) + a;
    const stringB: string = (b < 10 ? '0' : '') + b;
    return stringA + '.' + stringB;
  }

  test() {
    this.el.nativeElement.style.color = '#f77';
    this.websocketService.sendMessageThroughWebsocket(`Hi, I'm ${this.symbol}.`);
  }
}
