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
  instrument: string;
  symbol: string;
  fu: Fundamental = new Fundamental();
  po: Position = new Position();
  last_trade_price = -.1;
  estimate_low = -.1;
  estimate_high = -.1;
  hideMatches = false;
  orders: Array<Order> = [];
  matchId2mId: { [key: string]: string; } = {};
  buysell: { quantity: number, price: number } = { quantity: null, price: null};
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
      else if (message.QUOTE) {
        this.last_trade_price = message.QUOTE.last_trade_price;
        this.instrument = message.QUOTE.instrument;
      }
      else if (message.ORDERS) this.orders = (message.ORDERS as Array<any>).map(v => {
          let mId: string;
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
        });
      else if (message.ESTIMATE) {
        this.estimate_low = message.ESTIMATE.low;
        this.estimate_high = message.ESTIMATE.high;
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  buySell() {
    const action = this.buysell.price < this.last_trade_price ? 'BUY' : 'SELL';
    this.websocketService.sendMessageThroughWebsocket(
      `${action}: ${this.symbol}: ${this.buysell.quantity} ${this.buysell.price} ${this.instrument}`
    );
    setTimeout(() => {
      this.buysell.quantity = null;
      this.buysell.price = null;
      console.log('.');
    }, 3000);
  }

  calculateBuSeButtonClass(): string {
    if (this.buysell.quantity != null && this.buysell.price != null) {
      return this.buysell.price > this.last_trade_price ? 'btn-primary' : 'btn-danger';
    }
    else return '';
  }

  calculateOrderCssClass(o: Order): string {
    return [
      o.side,
      (typeof o.matchId) !== 'undefined' ? 'matched' : '',
      (typeof o.matchId) !== 'undefined' && this.hideMatches ? 'hide' : '',
      o.state.indexOf('confirmed') >= 0 ? 'confirmed' : ''
    ].join(' ').trim();
  }

  cancelOrder(orderId: string) {
    this.websocketService.sendMessageThroughWebsocket(`CANCEL: ${orderId}`);
  }

  exportOrders() {
    // returns a string which is s with spaces appended to it so that its length is l
    function appendSpaces(s: string, l: number) {
      return s + ' '.repeat(l - s.length);
    }

    const array1 = this.orders
      .map(o => {
        const s = parseInt(o.id.replace('-', ''), 16).toString(36);
        const id = s.substring(0, 4) + '-' + s.substring(4);
        const mId = o.mId === undefined ? 'None' : `Some("${o.mId}")`;
        return `new OrderElement("${o.created_at.replace(/\.\d+Z$/, '')}", "${id}", ${o.cumulative_quantity}, "${o.state}", ${o.price}, "${o.side}", ${mId})`
          .split(', ')
          .map(e => e + ',')
      });
    /*
     * array1 looks like this
     * [
     *   ["new OrderElement(\"2018-09-05T03:44:55\",", "\"7890-abcdef\",", "4,", "\"confirmed\",", "5.27,", "\"sell\",", "None),"],
     *   ["new OrderElement(\"2018-09-04T11:22:33\",", "\"abcd-defghi\",", "3,", "\"filled\",", "5.12,", "\"buy\",", "Some(\"w2\")),"],
     *   ["new OrderElement(\"2018-09-04T10:07:04\",", "\"abcd-defghi\",", "3,", "\"filled\",", "5.13,", "\"sell\",", "Some(\"w2\")),"]
     * ]
     */
    const sizes = array1[0]
      .map((v, i) => array1.map(row => row[i]))
      .map(row => row.map(s => s.length))
      .map(row => row.reduce((acc, v) => (acc < v) ? v : acc));

    console.log(array1
      .map(row => {
        const s =row.map((e, i) => appendSpaces(e, sizes[i])).join(' ');
        console.log(`- ${s}`);
        return s;
      })
      .join('\n')
    )
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
    const b = this.last_trade_price <= this.fu.open;
    const sv = {
      // s, v are from 0 - 100
      s: b ? 0 : Math.min((this.last_trade_price - this.fu.open) / this.fu.open*600, 100),
      v: b ? 100 - Math.min((this.fu.open - this.last_trade_price) / this.fu.open*500, 100) : 100   // from 0 - 100
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
