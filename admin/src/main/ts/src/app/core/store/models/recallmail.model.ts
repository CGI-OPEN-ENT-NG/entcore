
interface IMessage {
    mailId: string;
    subject: string;
    mail_date: number;
}

interface IAction {
    approved: boolean,
    date: number,
    tasks: ITask,
    userId: string,

}

interface ITask {
    finished: number;
    error: number;
    total: number;
}

export class RecallMail {
    recallMailId: number;
    userName: string;
    comment: string;
    message: IMessage;
    statutDisplayed?: string;
    status: string;
    action: IAction;
}

export function getActionStatus(action: IAction): string {
    let tasks: ITask = action.tasks;
    if (!action.approved) {
        return "WAITING";
    }
    if (tasks.finished === tasks.total) {
        return "REMOVED";
    } else if (tasks.error === 0) {
        return "PROGRESS";
    } else {
        return "ERROR";
    }
}

