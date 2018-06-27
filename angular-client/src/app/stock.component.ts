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
  lastTradePrice: number = 0;
  updatedAt: string;
  open: number = 0;
  low: number = 0;
  high: number = 0;
  quantity: number = 0;
  orders: Array<Order> = [];
  buysell: { quantity: number, price: number} = { quantity: null, price: null };
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
          this.setThisComponentBackgroundColor();
        }
        else if (type === 'POSITION') {
          this.quantity = x.quantity;
        }
        else if (type === 'FUNDAMENTAL') {
          this.open = x.open;
          this.low = x.low;
          this.high = x.high;
          this.setThisComponentBackgroundColor();
        }
        else if (type == 'ORDERS') {
          this.orders = x;
        }
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  buySell() {
    const action = this.buysell.price < this.lastTradePrice ? 'buy' : 'sell';
    console.log(`Going to ${action} ${this.buysell.quantity} shares ${this.symbol} at ${this.buysell.price}/share`)
  }

  cancelOrder(orderId: string) {
    this.websocketService.sendMessageThroughWebsocket(`CANCEL: ${orderId}`);
  }

  calculateClasses(order: Order): string {
    let result = 'row';
    result = result + ' ' + order.side;
    if (order.state === 'confirmed') {
      result = result + ' confirmed';
    }
    if (order.matchId && order.matchId != "") {
      result = result + ' matched'
    }
    return result;
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

  // ts can be in these formats 2018-04-19T19:04:41.123456Z
  minifyTimestamp(ts: string): string {
    return ts.replace(/^2018-/, '')
      .replace('T', ' ')
      .replace(/\.\d+Z$/, '')
      .replace(/Z$/, '');
  }

  setThisComponentBackgroundColor() {
    if (this.open === 0 || this.lastTradePrice === 0) {
      return;
    }

    const h = this.lastTradePrice > this.open ? 82 : 0;
    const sv = {
      // s, v are from 0 - 100
      s: this.lastTradePrice <= this.open ? 0 : Math.min((this.lastTradePrice - this.open)/this.open*600, 100),
      v: this.lastTradePrice <= this.open ? 100 - Math.min((this.open - this.lastTradePrice)/this.open*500, 100) : 100   // from 0 - 100
    };
    const sl = {
      s: -1,
      l: (2 - sv.s / 100) * sv.v / 2
    };
    sl.s = sv.s * sv.v / (sl.l < 50 ? sl.l * 2 : 200 - sl.l * 2);
    this.el.nativeElement.style.backgroundColor = `hsl(${h}, ${sl.s}%, ${sl.l}%)`;
  }

  trackByFunction(i: number, order: Order): string {
    return order.createdAt;
  }

  test() {
    this.el.nativeElement.style.color = '#f77';
    this.websocketService.sendMessageThroughWebsocket(`Hi, I'm ${this.symbol}.`);
  }
}
