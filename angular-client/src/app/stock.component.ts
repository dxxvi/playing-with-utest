import {Component, ElementRef, Input, OnDestroy, OnInit} from '@angular/core';
import {WebsocketService} from "./websocket.service";
import {Subscription} from 'rxjs';

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
  private subscription: Subscription;

  @Input('_symbol') set _symbol(value: string) {
    this.symbol = value.trim().toUpperCase();
  }

  constructor(private websocketService: WebsocketService, private el: ElementRef) {
  }

  ngOnInit() {
    this.subscription = this.websocketService.getSubject(this.symbol).asObservable().subscribe(message => {
      console.log(`Component ${this.symbol} got a message ${message.text}`);
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
        else if (type === '....') {

        }
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  test() {
    this.el.nativeElement.style.color = '#f77';
    this.websocketService.sendMessageThroughWebsocket(`Hi, I'm ${this.symbol}.`);
  }
}
