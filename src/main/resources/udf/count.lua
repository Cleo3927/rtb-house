local function agggregate_stats()
    return function(out, rec)
        local time = rec['time']

        if out[time] == nil then
            out[time] = map{total = 0, count = 0}
        end

        out[time]['count'] = out[time]['count'] + rec['count']
        out[time]['total'] = out[time]['total'] + rec['price']

        return out
    end
end

local function reduce_stats(a, b)
    return map.merge(a, b, function(v1, v2)
        return map{total = v1['total'] + v2['total'], count = v1['count'] + v2['count']}
    end)
end

local function filter_stats(origin, brandId, categoryId)
    return function(rec)
           return (origin == nil or rec['origin'] == origin) and
                  (brandId == nil or rec['brand_id'] == brandId) and
                  (categoryId == nil or rec['category_id'] == categoryId)
    end
end

function count(stream, origin, brandId, categoryId)
    return stream :
           filter(filter_stats(origin, brandId, categoryId)) :
           aggregate(map{}, agggregate_stats()) :
           reduce(reduce_stats)
end


local function filter_profile(action, begin_time, end_time)
    return function(rec)
        return rec['time'] >= begin_time and rec['time'] < end_time and rec['action'] == action
    end
end

local function sort_list(out, begin_list, end_list)
    if end_list - begin_list < 2 then
        return out
    end

    local pivot = (begin_list + end_list) / 2
    sort_list(out, begin_list, pivot)
    sort_list(out, pivot, end_list)

    local begin_first = begin_list
    local begin_second = pivot

    local buffer = list(end_list - begin_list)
    for i = begin_list, end_list - 1 do
        buffer[i] = out[i]
    end

    for i = begin_list, end_list - 1 do
        if begin_first < pivot and (begin_second < end_list and buffer[begin_first] > buffer[begin_second]) then
            out[i] = buffer[begin_first]
            begin_first = begin_first + 1
        else
            out[i] = buffer[begin_second]
            begin_second = begin_second + 1
        end
    end

    return out
end

local function limit_list(out, limit)
    list.trim(out, limit + 1)
end

local function aggregate_profile(limit)
    return function(out, rec)
        local rec_map = map{
                time = rec['time'],
                cookie = rec['cookie'],
                country = rec['country'],
                device = rec['device'],
                action = rec['action'],
                origin = rec['origin'],
                product_id = rec['product_id'],
                brand_id = rec['brand_id'],
                category_id = rec['category_id'],
                price = rec['price']
            }
        list.append(out, rec_map)

        if list.size(out) >= limit * 2 then
            sort_list(out, 1, list.size(out) + 1)
            limit_list(out, limit)
        end

        return out
    end
end

local function merge_and_limit(limit)
    return function(out, b)
        list.concat(out, b)
        if list.size(out) >= 2 * limit then
            sort_list(out, 1, list.size(out) + 1)
            limit_list(out, limit)
        end
        return out
    end
end

function sort_and_limit(stream, action, begin_time, end_time, limit)
    return stream :
           filter(filter_profile(action, begin_time, end_time)) :
           aggregate(list(), aggregate_profile(limit)) :
           reduce(merge_and_limit(limit))
end
