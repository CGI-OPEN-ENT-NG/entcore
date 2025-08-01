import { QueryClient } from '@tanstack/react-query';
import {
  LoaderFunctionArgs,
  useParams,
  useSearchParams,
} from 'react-router-dom';
import { MessageList } from '~/features/message-list/MessageList';
import { MessageListSkeleton } from '~/features/message-list/MessageListSkeleton';
import { MessageListEmpty } from '~/features/message-list/components/MessageListEmpty';
import { MessageListHeader } from '~/features/message-list/components/MessageListHeader';
import { folderQueryOptions, useFolderMessages } from '~/services';

export const loaderForSystemFolders =
  (queryClient: QueryClient) => async (args: LoaderFunctionArgs) => {
    const { params } = args;
    const validSystemFolders = ['inbox', 'outbox', 'draft', 'trash'];

    if (!params.folderId || !validSystemFolders.includes(params.folderId)) {
      return null;
    }

    return loader(queryClient)(args);
  };

export const loader =
  (queryClient: QueryClient) =>
  async ({ params, request }: LoaderFunctionArgs) => {
    const { searchParams } = new URL(request.url);
    const search = searchParams.get('search');
    const unread = searchParams.get('unread');
    if (params.folderId) {
      const messagesQuery = folderQueryOptions.getMessages(params.folderId, {
        search: search && search !== '' ? search : undefined,
        unread: unread === 'true' ? true : undefined,
      });
      queryClient.ensureInfiniteQueryData(messagesQuery);
    }
    return null;
  };

export function Component() {
  const { folderId } = useParams();
  const [searchParams] = useSearchParams();
  const {
    messages,
    isPending: isLoadingMessage,
    isFetchingNextPage,
  } = useFolderMessages(folderId!);

  if (isLoadingMessage && messages === undefined) {
    // If messages are still loading and not yet defined, we return a skeleton.
    return <MessageListSkeleton />;
  }

  return (
    <>
      {(!!messages?.length ||
        searchParams.get('search') ||
        searchParams.get('unread')) && <MessageListHeader />}
      <MessageList />
      {!isLoadingMessage && !messages?.length && <MessageListEmpty />}
      {isFetchingNextPage && <MessageListSkeleton withHeader={false} />}
    </>
  );
}
