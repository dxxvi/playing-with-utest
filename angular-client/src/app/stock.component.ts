import {Component, ElementRef, Input, OnDestroy, OnInit} from '@angular/core';
import {WebsocketService} from './websocket.service';
import {Subscription} from 'rxjs';
import {Fundamental, Order, Position} from './model';

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
  hideMatches: boolean = false;
  orders: Array<Order> = [];
  matchId2mId: { [key:string]: string; } = {};
  private subscription: Subscription;

  @Input('_symbol') set _symbol(value: string) {
    this.symbol = value.trim().toUpperCase();
  }

  constructor(private websocketService: WebsocketService, private el: ElementRef) {
  }

  ngOnInit() {
    this.subscription = this.websocketService.getSubject(this.symbol).asObservable().subscribe(message => {
      if (message.POSITION) this.po.quantity = message.POSITION.quantity;
      else if (message.FUNDAMENTAL) {
        const f = message.FUNDAMENTAL;
        if (f.low && f.low > 0) this.fu.low = f.low;
        if (f.high && f.high > 0) this.fu.high = f.high;
        if (f.open && f.open > 0) this.fu.open = f.open;
      }
      else if (message.QUOTE) this.last_trade_price = message.QUOTE.last_trade_price;
      else if (message.ORDERS) {
        this.orders = (message.ORDERS as Array<any>).map(v => {
          let mId: string = undefined;
          if (typeof v.matchId !== 'undefined') {
            mId = this.matchId2mId[v.matchId];
            if (typeof mId === 'undefined') {
              mId = Math.floor(Math.random() * 1296).toString(36);
              this.matchId2mId[v.matchId] = mId;
            }
          }
          return {
            created_at: v.created_at,
            id: v.id,
            cumulative_quantity: v.cumulative_quantity,
            quantity: v.quantity,
            state: v.state,
            price: v.price,
            average_price: v.average_price,
            side: v.side,
            matchId: v.matchId,
            mId: mId
          };
        })
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  calculateOrderCssClass(o: Order): string {
    return [
      o.side,
      (typeof o.matchId) !== 'undefined' ? 'matched' : '',
      (typeof o.matchId) !== 'undefined' && this.hideMatches ? 'hide' : '',
      o.state.indexOf('confirmed') >= 0 ? 'confirmed' : ''
    ].join(' ').trim();
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
    return order.created_at;
  }

}
