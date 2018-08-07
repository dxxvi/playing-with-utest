import {Component, ElementRef, Input, OnDestroy, OnInit} from '@angular/core';
import {WebsocketService} from './websocket.service';
import {Subscription} from 'rxjs';
import {Order} from './order';
import {Fundamental, Position} from './model';

@Component({
  selector: 'li.stock-panel',
  templateUrl: './stock.component.html',
  styleUrls: ['./stock.component.css']
})
export class StockComponent implements OnInit, OnDestroy {
  symbol: string;
  fu: Fundamental = new Fundamental();
  po: Position = new Position();
  last_trade_price: number = -.1;
  private subscription: Subscription;

  @Input('_symbol') set _symbol(value: string) {
    this.symbol = value.trim().toUpperCase();
  }

  constructor(private websocketService: WebsocketService, private el: ElementRef) {
  }

  ngOnInit() {
    this.subscription = this.websocketService.getSubject(this.symbol).asObservable().subscribe(message => {
      if (message.POSITION) {
        const p = message.POSITION;
        this.po.quantity = p.quantity;
      }
      else if (message.FUNDAMENTAL) {
        const f = message.FUNDAMENTAL;
        if (f.low && f.low > 0) this.fu.low = f.low;
        if (f.high && f.high > 0) this.fu.high = f.high;
        if (f.open && f.open > 0) this.fu.open = f.open;
      }
      else if (message.QUOTE) {
        this.last_trade_price = message.QUOTE.last_trade_price;
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

  // ts is in this format 2018-04-19T19:04:41.123456Z
  minifyTimestamp(ts: string): string {
    return ts.replace(/^2018-/, '')
      .replace('T', ' ')
      .replace(/\.\d+Z$/, '')
      .replace(/Z$/, '');
  }

  backgroundColor() {
    if (this.fu.open <= 0 || this.last_trade_price <= 0) {
      return;
    }

    const h = this.last_trade_price > this.fu.open ? 82 : 0;
    const sv = {
      // s, v are from 0 - 100
      s: this.last_trade_price <= this.fu.open ? 0 : Math.min((this.last_trade_price - this.fu.open)/this.fu.open*600, 100),
      v: this.last_trade_price <= this.fu.open ? 100 - Math.min((this.fu.open - this.last_trade_price)/this.fu.open*500, 100) : 100   // from 0 - 100
    };
    const sl = {
      s: -1,
      l: (2 - sv.s / 100) * sv.v / 2
    };
    sl.s = sv.s * sv.v / (sl.l < 50 ? sl.l * 2 : 200 - sl.l * 2);
    return `hsl(${h}, ${sl.s}%, ${sl.l}%)`;
  }

  symbolClass(): Array<String> {
    return WebsocketService.DOW_STOCKS.includes(this.symbol) ? ['dow'] : [];
  }

  trackByFunction(i: number, order: Order): string {
    return order.createdAt;
  }

}
