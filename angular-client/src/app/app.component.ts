import {Component, OnDestroy, OnInit} from '@angular/core';
import {WebsocketService} from './websocket.service';
import {Subscription} from 'rxjs';

@Component({
  selector: 'div.app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, OnDestroy {
  symbols: Array<string> = [];
  private symbolFoundSubscription: Subscription;

  constructor(private websocketService: WebsocketService) {
  }

  ngOnInit(): void {
    this.websocketService.getSubject('SYMBOL_FOUND').asObservable().subscribe(
      value => {
        const symbol = value.text;
        if (!this.symbols.includes(symbol)) {
          this.symbols.push(symbol);
          this.symbols.sort();
        }
      }
    )
  }

  ngOnDestroy(): void {
    this.symbolFoundSubscription.unsubscribe();
  }

  trackByFunction(i: number, symbol: string): string {
    return symbol;
  }
}
