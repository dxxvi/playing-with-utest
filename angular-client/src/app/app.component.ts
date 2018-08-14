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
  notices: Array<{uuid: string, message: string, level: string}> = [];
  private symbolFoundSubscription: Subscription;
  private noticeAddSubscription: Subscription;
  private noticeDeleteSubscription: Subscription;

  constructor(private websocketService: WebsocketService) {
  }

  ngOnInit(): void {
    this.symbolFoundSubscription = this.websocketService.getSubject('SYMBOL_FOUND').asObservable().subscribe(
      value => {
        let isNewSymbol = false;
        const symbol = value.symbol;
        if (value.rest.startsWith('POSITION: ')) {
          const position = JSON.parse(value.rest.replace('POSITION: ', ''));
          isNewSymbol = this.addSymbolIfNotExist(symbol);
          setTimeout(() => {
            this.websocketService.sendMessageThroughSubject(symbol, { POSITION: position })
          }, isNewSymbol ? 200 : 1);
        }
        else if (value.rest.startsWith('FUNDAMENTAL: ')) {
          const fundamental = JSON.parse(value.rest.replace('FUNDAMENTAL: ', ''));
          isNewSymbol = this.addSymbolIfNotExist(symbol);
          setTimeout(() => {
            this.websocketService.sendMessageThroughSubject(symbol, { FUNDAMENTAL: fundamental })
          }, isNewSymbol ? 200 : 1);
        }
        else if (value.rest.startsWith('QUOTE: ')) {
          const quote = JSON.parse(value.rest.replace('QUOTE: ', ''));
          isNewSymbol = this.addSymbolIfNotExist(symbol);
          setTimeout(() => {
            this.websocketService.sendMessageThroughSubject(symbol, { QUOTE: quote })
          }, isNewSymbol ? 200 : 1);
        }
        else if (value.rest.startsWith('ORDERS: ')) {
          const orders = JSON.parse(value.rest.replace('ORDERS: ', ''));
          isNewSymbol = this.addSymbolIfNotExist(symbol);
          setTimeout(() => {
            this.websocketService.sendMessageThroughSubject(symbol, { ORDERS: orders})
          }, isNewSymbol ? 200 : 1);
        }
        else {
          console.log(`Unknown message for ${symbol}: ${value.rest}`)
        }
      }
    );

    this.noticeAddSubscription = this.websocketService.getSubject('NOTICE_ADD').asObservable().subscribe(value => {
      const i = value.indexOf(': ');
      if (i > 0) {
        const level = value.substring(0, i).toLowerCase();
        const message = value.substr(i + 2);
        this.notices.push({uuid: this.generateUUID(), message: message, level: level});
      }
      else {
        console.error(`Unknown notice format: ${value}`)
      }
    });

    this.noticeDeleteSubscription = this.websocketService.getSubject('NOTICE_DELETE').asObservable().subscribe(value => {
      this.removeNotice(value);
    });
  }

  ngOnDestroy(): void {
    this.symbolFoundSubscription.unsubscribe();
    this.noticeAddSubscription.unsubscribe();
    this.noticeDeleteSubscription.unsubscribe();
  }

  /**
   * @return true if this is a new symbol
   */
  addSymbolIfNotExist(symbol: string): boolean {
    if (!this.symbols.includes(symbol)) {
      this.symbols.push(symbol);
      this.symbols.sort();
      return true;
    }
    return false;
  }

  calculateNoticeClass(level: string) {
    if (level === 'primary') {
      return ['primary'];
    }
    else if (level === 'danger') {
      return ['danger'];
    }
    else {
      return ['light'];
    }
  }

  removeNotice(uuid: string) {
    const i = this.notices.findIndex(v => v.uuid === uuid);
    if (i >= 0) {
      this.notices.splice(i, 1);
    }
  }

  removeSymbol(symbol: string) {
    const i = this.symbols.findIndex(e => e === symbol);
    if (i >= 0) this.symbols.splice(i, 1);
  }

  trackByFunction(i: number, symbol: string): string {
    return symbol;
  }

  trackByNoticeFunction(i: number, n: {uuid: string, message: string, level: string}): string {
    return n.uuid;
  }

  private generateUUID(): string {
    const s4 = function(): string {
      return Math.floor((1 + Math.random()) * 0x10000)
      .toString(16)
      .substring(1);
    };
    return s4() + s4() + '-' + s4() + '-' + s4() + '-' + s4() + '-' + s4() + s4() + s4();
  }
}
