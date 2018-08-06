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
  private subscription: Subscription;

  @Input('_symbol') set _symbol(value: string) {
    this.symbol = value.trim().toUpperCase();
  }

  constructor(private websocketService: WebsocketService, private el: ElementRef) {
  }

  ngOnInit() {
    this.subscription = this.websocketService.getSubject(this.symbol).asObservable().subscribe(message => {
      console.log(`Component ${this.symbol} got a message ${message}`);
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

/*
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
*/

  trackByFunction(i: number, order: Order): string {
    return order.createdAt;
  }

}
