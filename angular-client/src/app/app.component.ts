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
        const symbol = value.text.symbol;
        if (value.text.rest.startsWith('POSITION: ')) {
          const position = JSON.parse(value.text.rest.replace('POSITION: ', ''));
          if (position.quantity > 0) this.addSymbolIfNotExist(symbol);
          // if quantity = 0, this stock might not be in the default watch list. It appears here because we had it before.
        }
        else this.addSymbolIfNotExist(symbol);
      }
    )
  }

  ngOnDestroy(): void {
    this.symbolFoundSubscription.unsubscribe();
  }

  addSymbolIfNotExist(symbol: string) {
    if (!this.symbols.includes(symbol)) {
      this.symbols.push(symbol);
      this.symbols.sort();
    }
  }

  removeSymbol(symbol: string) {
    const i = this.symbols.findIndex(e => e === symbol);
    if (i >= 0) this.symbols.splice(i, 1);
  }

  trackByFunction(i: number, symbol: string): string {
    return symbol;
  }
}
