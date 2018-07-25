import {Component, Input, OnInit} from '@angular/core';
import {WebsocketService} from "../websocket.service";

@Component({
  selector: 'div.notice',
  templateUrl: './notice.component.html',
  styleUrls: ['./notice.component.css']
})
export class NoticeComponent implements OnInit {
  @Input() uuid: string;
  @Input() message: string;
  @Input() level: string;

  constructor(private websocketService: WebsocketService) { }

  ngOnInit() {
    setTimeout(() => {
      this.websocketService.sendMessageThroughSubject('NOTICE_DELETE', this.uuid);
    }, 3456);
  }
}
