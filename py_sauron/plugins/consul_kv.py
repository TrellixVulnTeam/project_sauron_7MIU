import socket

from consul import Consul, ConsulException

from primitives.item_primitives import join_prefix, item_action, Result, Item, split_by_sep


CONSUL_SEP = '/'


def is_consul_prefix(s_item):
    """
    Verify that the item's prefix is appropriate for consul
    return a valid result if it is, return an invalid Result if it isn't
    """

    if s_item.prefix is None:
        return Result(result = item)
    if s_item.prefix[0] != '/':
        return Result(result = item)
    return Result(invalid = item)

def _put_consul(s_item, conn):
    n_item = join_prefix(s_item, '/')
    try:
        r = conn.kv.put(n_item.key, item.value)
        if r:
            return Result(s_item)
        else:
            return Result(invalid=s_item)
    except (ConsulException, socket.error) as e:
        return Result(invalid=s_item,
                      exception=e)

def _get_consul(s_item, conn, recurse=False):
    """
    If recurse is set, we will return all items matching this item's prefix
    We may want validation to ensure we can't dump all of consul's key/value pairs
    """

    if recurse and s_item.prefix is not None:
        c_key = s_item.prefix
    elif recurse and s_item.prefix is None:
        return []
    else:
        c_key = join_prefix(s_item, CONSUL_SEP).key

    try:
        index, data = conn.kv.get(c_key, recurse=recurse)
    except (ConsulException, socket.error) as e:
        return Result(invalid=s_item,
                      exception=e)
    
    def to_item(intermediate_res):
        return  Item(key=intermediate_res['Key'],
                     value = intermediate_res['Value'].decode())
    if data:
        if recurse:
            r_items = map(to_item, data)
        else:
            r_items = to_item(data)
        result = map(lambda x: split_by_sep(x, CONSUL_SEP), r_items)
        return Result(result)
    return Result(invalid=item)

def get_consul_by_prefix(s_item, conn=Consul()):
    """
    Query consul recursively to get anything matching the items's prefix
    """
    return _get_consul(s_item, conn, recurse=True)

def get_consul(s_item, conn=Consul()):
    """
    Query consul for the prefix and key of the provided item
    """
    return _get_consul(s_item, conn)


def put_consul(s_item, conn=Consul()):
    """
    Write an item to consul
    """
    return _put_consul(s_item, conn)

        
