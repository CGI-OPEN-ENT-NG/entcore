import { useDate, useEdificeClient } from '@edifice.io/react';
import { useLayoutEffect } from 'react';
import { Group, Message, Recipients, User } from '~/models';
import {
  createDefaultMessage,
  useMessageQuery,
  useSignaturePreferences,
} from '~/services';
import { useMessageStore } from '~/store/messageStore';
import { useAdditionalRecipients } from './useAdditionalRecipients';
import { useI18n } from './useI18n';
import { SIGNATURE_EMPTY_CONTENT } from '~/components/SignatureEditor';

export type UserAction = 'reply' | 'replyAll' | 'transfer';
export interface MessageReplyOrTransferProps {
  messageId: string | undefined;
  action?: UserAction;
}

export function useInitMessage({
  messageId,
  action,
}: MessageReplyOrTransferProps) {
  const { data: messageOrigin, isFetching } = useMessageQuery(messageId || '');
  const { currentLanguage, user, userProfile } = useEdificeClient();
  const { t, common_t } = useI18n();
  const { formatDate } = useDate();
  const { data: signatureData, isPending: getSignatureIsPending } =
    useSignaturePreferences();
  const message = useMessageStore.use.message();
  const setMessage = useMessageStore.use.setMessage();

  // Get IDs of users and groups/favorites to add as recipients.
  const { recipients: recipientsToAddToMessage } = useAdditionalRecipients();

  const signature =
    signatureData?.useSignature && signatureData.signature
      ? `${SIGNATURE_EMPTY_CONTENT}${SIGNATURE_EMPTY_CONTENT}${signatureData.signature}`
      : SIGNATURE_EMPTY_CONTENT;

  useLayoutEffect(() => {
    // If the configuration for the signature is pending, we return an empty message
    if (getSignatureIsPending || !messageOrigin || isFetching) {
      return undefined;
    }

    if (messageOrigin.id && !action) {
      // We are in case of a read message
      setMessage({
        ...messageOrigin,
      });
    } else {
      // We are in the case of a new message or a reply, replayAll or transfer
      const messageTmp: Message = {
        ...createDefaultMessage(signature),
        language: currentLanguage,
        parent_id: messageOrigin.id,
        thread_id: messageOrigin.id,
        from: {
          id: user?.userId || '',
          displayName: user?.username || '',
          profile: (userProfile || '') as string,
        },
      };

      const displayRecipient = (recipients: Recipients) => {
        const usersDisplayName = recipients.users
          .map((user) => user.displayName)
          .join(', ');
        const groupsDisplayName = recipients.groups
          .map((group) => group.displayName)
          .join(', ');
        return (
          usersDisplayName +
          (recipients.users.length > 0 && recipients.groups.length > 0
            ? ', '
            : '') +
          groupsDisplayName
        );
      };

      let body = `${signature}`;
      if (messageOrigin.id && action) {
        if (action === 'transfer') {
          // We are in the case of a transfer
          body =
            body +
            `<div>
            ${signatureData?.useSignature ? SIGNATURE_EMPTY_CONTENT : ''}
            <p><span style="font-size: 14px; font-weight:400;">--------- ${t('transfer.title')} ---------</span></p>
            <p><span style="font-size: 14px; font-weight:400;">${t('transfer.from') + (messageOrigin.from?.displayName || '')}</span></p>
            <p><span style="font-size: 14px; font-weight:400;">${t('transfer.date') + (messageOrigin.date ? formatDate(messageOrigin.date, 'LLL') : '')}</span></p>
            <p><span style="font-size: 14px; font-weight:400;">${t('transfer.subject') + messageOrigin.subject}</span></p>
            <p><span style="font-size: 14px; font-weight:400;">${t('transfer.to') + displayRecipient(messageOrigin.to)}</span></p>
            ${messageOrigin.cc.users.length || messageOrigin.cc.groups.length ? '<p><span style="font-size: 14px; font-weight:400;">' + t('transfer.cc') + displayRecipient(messageOrigin.cc) + '</span></p>' : ''}
            ${messageOrigin.body}
        </div>`;
          messageTmp.to.users = [];
          messageTmp.to.groups = [];
          messageTmp.cc.users = [];
          messageTmp.cc.groups = [];
          messageTmp.cci = undefined;
          messageTmp.attachments = messageOrigin.attachments;
        } else {
          // We are in the case of a reply or replyAll
          body =
            body +
            `<div class="conversation-history">
          <p><span style="font-size: 14px; font-weight:400;"><em>${t('from') + ' ' + (messageOrigin.from?.displayName || '') + (messageOrigin.date ? ', ' + common_t('date.format.pretty', { date: formatDate(messageOrigin.date, 'LL'), time: formatDate(messageOrigin.date, 'LT') }) : '')}</em></span></p>
          <p><span style="font-size: 14px; font-weight:400; color: #909090;"><em>${t('transfer.to') + displayRecipient(messageOrigin.to)}</em></span></p>
          ${messageOrigin.cc.users.length || messageOrigin.cc.groups.length ? '<p><span style="font-size: 14px; font-weight:400;color: #909090;"><em>' + t('transfer.cc') + displayRecipient(messageOrigin.cc) + '</em></span></p>' : ''}
          <div class="conversation-history-body">
            ${messageOrigin.body}
          </div>
        </div>`;

          switch (action) {
            case 'reply':
              messageTmp.to.users = messageOrigin.from
                ? [messageOrigin.from]
                : [];
              messageTmp.to.groups = [];
              messageTmp.cc.users = [];
              messageTmp.cc.groups = [];
              messageTmp.cci = undefined;
              break;
            case 'replyAll':
              messageTmp.to = {
                ...messageOrigin.to,
                users: [
                  ...messageOrigin.to.users.filter(
                    (user: User) => messageOrigin.from?.id !== user.id,
                  ),
                  ...(messageOrigin.from ? [messageOrigin.from] : []),
                ],
              };
              messageTmp.cc = { ...messageOrigin.cc };
              if (
                messageOrigin.from?.id === user?.userId &&
                messageOrigin.cci
              ) {
                messageTmp.cci = { ...messageOrigin.cci };
              }
              break;
          }
        }

        messageTmp.body = body;

        const prefixSubject =
          action === 'transfer'
            ? t('message.transfer.subject')
            : t('message.reply.subject');

        if (!messageOrigin.subject.startsWith(prefixSubject)) {
          messageTmp.subject = `${prefixSubject}${messageOrigin.subject}`;
        } else {
          messageTmp.subject = `${messageOrigin.subject}`;
        }
      }

      if (
        recipientsToAddToMessage?.users.length ||
        recipientsToAddToMessage?.groups.length
      ) {
        messageTmp.to = {
          users: [
            ...recipientsToAddToMessage.users,
            ...(messageTmp.to.users.filter(
              (user: User) =>
                !recipientsToAddToMessage.users.some((u) => u.id === user.id),
            ) || []),
          ],
          groups: [
            ...recipientsToAddToMessage.groups,
            ...(messageTmp.to.groups.filter(
              (group: Group) =>
                !recipientsToAddToMessage.groups.some((g) => g.id === group.id),
            ) || []),
          ],
        };
      }

      setMessage({
        ...messageTmp,
      });
    }

    return () => {
      setMessage(undefined);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    getSignatureIsPending,
    isFetching,
    messageOrigin,
    messageId,
    action,
    recipientsToAddToMessage,
  ]);

  return message;
}
