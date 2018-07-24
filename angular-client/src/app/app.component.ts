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
        const symbol = value.text.symbol;
        if (value.text.rest.startsWith('POSITION: ')) {
          const position = JSON.parse(value.text.rest.replace('POSITION: ', ''));
          if (position.quantity > 0) this.addSymbolIfNotExist(symbol);
          // if quantity = 0, this stock might not be in the default watch list. It appears here because we had it before.
        }
        else this.addSymbolIfNotExist(symbol);
      }
    );

    this.noticeAddSubscription = this.websocketService.getSubject('NOTICE_ADD').asObservable().subscribe(value => {
      const i = value.text.indexOf(': ');
      if (i > 0) {
        const level = value.text.substring(0, i).toLowerCase();
      }
      else {
        console.error(`Unknown notice format: ${value.text}`)
      }
    });

    this.noticeDeleteSubscription = this.websocketService.getSubject('NOTICE_DELETE').asObservable().subscribe(value => {

    });
  }

  ngOnDestroy(): void {
    this.symbolFoundSubscription.unsubscribe();
    this.noticeAddSubscription.unsubscribe();
    this.noticeDeleteSubscription.unsubscribe();
  }

  addSymbolIfNotExist(symbol: string) {
    if (!this.symbols.includes(symbol)) {
      this.symbols.push(symbol);
      this.symbols.sort();
    }
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

  newNotice() {
    const message = Date.now() + ' ';
    const level = 'debug';
    this.notices.push({uuid: this.generateUUID(), message: message, level: level});
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
