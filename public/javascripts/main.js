// submit synchronously to a provided URL (action) and parameters.
function submit(method, action, parameters) {
  var form = $('<form></form>');

  form.attr("method", method);
  form.attr("action", action);

  if (parameters)
    addParams(form, parameters)

  $(document.body).append(form);
  form.submit();
}

function addParams(form, parameters) {
  $.each(parameters, function(key, value) {
    function addField(val) {
        var field = $('<input></input>');

        field.attr("type", "hidden");
        field.attr("name", key);
        field.attr("value", val);

        form.append(field);
    }

    if (Array.isArray(value)) {
        $.each(value, function(index, val) {
            addField(val);
        });
    } else {
        addField(value);
    }
  });
}

function getQueryParams(qs) {
  qs = qs.split('+').join(' ');
  qs = qs.split("?").slice(1).join("?");

  var params = {},
      tokens,
      re = /[?&]?([^=]+)=([^&]*)/g;


  while (tokens = re.exec(qs)) {
    var paramName = decodeURIComponent(tokens[1]).replace("amp;", "");
    var value = decodeURIComponent(tokens[2]);
    var existingValue = params[paramName];

    if (existingValue) {
        if (Array.isArray(existingValue)) {
            existingValue.push(value)
            params[paramName] = existingValue;
        } else {
            params[paramName] = [existingValue, value]
        }
    } else
        params[paramName] = value;
  }

  return params;
}

function addUrlParm(url, name, value) {
    var re = new RegExp("([?&]" + name + "=)[^&]+", "");

    function add(sep) {
        url += sep + name + "=" + encodeURIComponent(value);
    }

    function change() {
        url = url.replace(re, "$1" + encodeURIComponent(value));
    }
    if (url.indexOf("?") === -1) {
        add("?");
    } else {
        if (re.test(url)) {
            change();
        } else {
            add("&");
        }
    }
}

function getCoreURL(url) {
    var index = url.indexOf("?")
    if (url.indexOf("?") != -1) {
        return url.substring(0, index)
    }
    return url
}

function stringDatumTokenizer(searchAsContainsFlag, nonWhitespaceDelimiter, string) {
    var string2 = string;
    if (nonWhitespaceDelimiter) {
        string2 = string.split(nonWhitespaceDelimiter).join(" ")
    }
    var strings = Bloodhound.tokenizers.whitespace(string2);
    if (searchAsContainsFlag) {
        $.each(strings, function (k, v) {
            var i = 1;
            while ((i + 1) < v.length) {
                strings.push(v.substr(i, v.length));
                i++;
            }
        })
    }
    return strings;
}

function createStringDatumTokenizer(searchAsContainsFlag, nonWhitespaceDelimiter) {
    return stringDatumTokenizer.bind(null, searchAsContainsFlag).bind(null, nonWhitespaceDelimiter)
}

function populateStringTypeahead(element, data, searchAsContainsFlag, nonWhitespaceDelimiter, updateValueElement) {
    var datumTokenizer = createStringDatumTokenizer(searchAsContainsFlag, nonWhitespaceDelimiter)
    var source = createBloodhoundSource(data, datumTokenizer)
    populateTypeahead(element, source, null, null, updateValueElement)
}

function createBloodhoundSource(data, datumTokenizer) {
    var dataSource = new Bloodhound({
        datumTokenizer: datumTokenizer,
        queryTokenizer: Bloodhound.tokenizers.whitespace,
        local: data
    });

    dataSource.initialize();

    // Setting of minlength to 0 does not work. To show ALL items if nothing entered this function needs to be introduced
    function listSearchWithAll(q, sync) {
        if (q == '')
            sync(dataSource.all());
        else
            dataSource.search(q, sync);
    }

    return listSearchWithAll;
}

function populateTypeahead(element, source, displayFun, suggestionFun, updateValueElement) {
    element.typeahead({
        hint: true,
        highlight: true,
        minLength: 0
    }, {
        source: source,
        display: displayFun,
        templates: {
            suggestion: suggestionFun
        },
        limit: 25
    });

    element.on("focus", function () {
        var value = element.val();
        element.typeahead('val', '_');
        element.typeahead('val', value);
        return true
    });

    element.on('typeahead:select', function (e, datum) {
        if (updateValueElement)
            updateValueElement(datum);
    });

    element.on('typeahead:cursorchanged', function (e, datum) {
        if (updateValueElement)
            updateValueElement(datum);
    });

    element.on('keyup', this, function(e) {
        if(e.keyCode != 8 && e.keyCode != 46) {
            selectFirstSuggestion(element)
        }
    })
}

function selectFirstSuggestion(element) {
    var suggestions = element.parent().find('.tt-suggestion')
    if (suggestions.length == 1) {
        element.typeahead('select', suggestions.first())
    }
}

function selectShortestSuggestion(element) {
    var suggestions = element.parent().find('.tt-suggestion')
    if (suggestions.length > 0) {
        var min = Number.MAX_SAFE_INTEGER
        var minSuggestion = null
        $.each(suggestions, function (index, suggestion) {
            if (suggestion.innerText.length < min) {
                minSuggestion = suggestion;
                min = suggestion.innerText.length
            }
        });
        element.typeahead('select', minSuggestion)
    }
}

function createFieldBloodhoundSource(fieldNameAndLabels, showOption) {
    var fullFieldNameAndLabels = fieldNameAndLabels.map( function(field, index) {

        var nameItem = (Array.isArray(field)) ?
            {key: field[0], value: field[0]} :
            {key: field.name, value: field.name};

        var labelItem = (Array.isArray(field)) ?
            {key: field[0], value: field[1], isLabel: true} :
            {key: field.name, value: field.label, isLabel: true};

        switch (showOption) {
            case 0: return [nameItem];
            case 1: return (labelItem.value != null) ? [labelItem] : [];
            case 2: return (labelItem.value != null) ? [labelItem] : [nameItem];
            case 3: return (labelItem.value != null) ? [nameItem, labelItem] : [nameItem];
        }
    });

    var stringDatumTokenizer = createStringDatumTokenizer(true, false);

    var compareValues = function (a, b) {
        if (a.value < b.value)
            return -1;
        else if (a.value == b.value)
            return 0;
        else
            return 1;
    };

    return createBloodhoundSource(
        [].concat.apply([], fullFieldNameAndLabels).sort(compareValues),
        function (item) {
            return stringDatumTokenizer(item.value);
        }
    )
}

function populateFieldTypeaheds(typeaheadElements, fieldNameElements, fieldNameAndLabels, showOption, initSelectByNameElement) {
    var source = createFieldBloodhoundSource(fieldNameAndLabels, showOption)

    for(var i = 0; i < typeaheadElements.length; i++){
        var typeaheadElement = typeaheadElements[i]
        var fieldNameElement = fieldNameElements[i]

        populateFieldTypeahedAux(typeaheadElement, fieldNameElement, source, showOption)

        if (initSelectByNameElement) {
            selectByNameElement(typeaheadElement, fieldNameElement, fieldNameAndLabels, showOption)
        }
    }
}

/**
 *
 * @param typeaheadElement
 * @param fieldNameElement
 * @param fieldNameAndLabels
 * @param showOption 0 - show field names only, 1 - show field labels only,
 *                   2 - show field labels, and field names if no label defined, 3 - show both, field names and labels
 */
function populateFieldTypeahed(typeaheadElement, fieldNameElement, fieldNameAndLabels, showOption, initSelectByNameElement) {
    var source = createFieldBloodhoundSource(fieldNameAndLabels, showOption)

    populateFieldTypeahedAux(typeaheadElement, fieldNameElement, source, showOption)

    if (initSelectByNameElement) {
        selectByNameElement(typeaheadElement, fieldNameElement, fieldNameAndLabels, showOption)
    }
}

function selectByNameElement(typeaheadElement, fieldNameElement, fieldNameAndLabels, showOption) {
    var fieldName = fieldNameElement.val();

    var matchedFieldNameLabel = $.grep(fieldNameAndLabels, function (nameLabel) {
        return nameLabel[0] == fieldName
    });

    if (matchedFieldNameLabel.length > 0) {
        var name = matchedFieldNameLabel[0][0]
        var label = matchedFieldNameLabel[0][1]

        var selectElement = null;

        switch (showOption) {
            case 0: selectElement = name; break;
            case 1: selectElement = label; break;
            case 2: selectElement = (label != null) ? label : name; break;
            case 3: selectElement = (label != null) ? label : name; break;
        }

        if (selectElement) {
            typeaheadElement.typeahead('val', selectElement);
            selectShortestSuggestion(typeaheadElement)
        }
    }
}

function populateFieldTypeahedAux(typeaheadElement, fieldNameElement, source, showOption) {
    populateTypeahead(
        typeaheadElement,
        source,
        function (item) {
            return item.value;
        },
        function (item) {
            var nameBadge = '';
            var labelBadge = '';
            switch (showOption) {
                case 0: nameBadge = ''; break;
                case 1: nameBadge = ''; break;
                case 2: nameBadge = '<span class="label label-info label-filter">name</span>'; break;
                case 3: nameBadge = '<span class="label label-info label-filter">name</span>'; break;
            }
            switch (showOption) {
                case 0: labelBadge = ''; break;
                case 1: labelBadge = ''; break;
                case 2: labelBadge = '<span class="label label-success label-filter">label</span>'; break;
                case 3: labelBadge = '<span class="label label-success label-filter">label</span>'; break;
            }
            if (item.isLabel)
                return '<div><span>' + item.value + '</span>' + labelBadge + '</div>';
            else
                return '<div><span>' + item.value + '</span>' + nameBadge + '</div>';
        },
        function (item) {
            fieldNameElement.val(item.key);
        }
    );
}

/**
 *
 * @param typeaheadElement
 * @param fieldNameElement
 * @param url
 * @param showOption 0 - show field names only, 1 - show field labels only,
 *                   2 - show field labels, and field names if no label defined, 3 - show both, field names and labels
 */
function populateFieldTypeahedFromUrl(typeaheadElement, fieldNameElement, url, showOption, postFunction) {
    $.ajax({
        url: url,
        success: function (fieldNameAndLabels) {
            populateFieldTypeahed(typeaheadElement, fieldNameElement, fieldNameAndLabels, showOption);
            if (postFunction) {
                postFunction()
            }
        },
        error: showErrorResponse
    });
}

function populateFieldTypeahedsFromUrl(typeaheadElements, fieldNameElements, url, showOption, initSelectByNameElement, postFunction) {
    $.ajax({
        url: url,
        success: function (fieldNameAndLabels) {
            populateFieldTypeaheds(typeaheadElements, fieldNameElements, fieldNameAndLabels, showOption, initSelectByNameElement);
            if (postFunction) {
                postFunction()
            }
        },
        error: showErrorResponse
    });
}

function populateIdNameTypeahedFromUrl(typeaheadElement, idElement, url) {
    $.ajax({
        url: url,
        success: function (data) {
            var typeaheadData = data.map(function (item, index) {
                return {name: item._id.$oid, label: item.name};
            });
            populateFieldTypeahed(typeaheadElement, idElement, typeaheadData, 1);
        },
        error: function(data){
            showErrorResponse(data)
        }
    });
}

function registerMessageEventSource(url) {
//  if (!!window.EventSource) {
 if (!window.messageSource)
    window.messageSource = new self.EventSource(url);
    window.messageSource.onmessage = function (e) {
      if (e.data) {
        var json = $.parseJSON(e.data);
        prependTrollboxJsonMessage(json, true);
        $("#trollmessagebox").scrollTop($(document).height());
      }
    };

    window.messageSource.addEventListener('error', function (e) {
      if (e.eventPhase == EventSource.CLOSED) {
        console.log("Connection was closed on error: ");
        console.log(e)
      } else {
        console.log("Error occurred while streaming: ");
        console.log(e)
      }
    }, false);
    //setTimeout(function() {
    //    console.log("Closing source");
    //    source.close()
    //}, 3000)
  //} else {
  //  console.log("No support for HTML-5 Event Source")
  //  prependTrollboxMessage("", "", "Sorry. This browser doesn't seem to support HTML5-based messaging. Check <a href='http://html5test.com/compare/feature/communication-eventSource.html'>html5test</a> for browser compatibility.");
  //}
}

function prependTrollboxJsonMessage(jsonMessage, isAdmin, fadeIn) {
    var createdBy = jsonMessage.createdByUser
    var isUserAdmin = jsonMessage.isUserAdmin
    var timeCreated = jsonMessage.timeCreated
    var content = Autolinker.link(jsonMessage.content);
    var date = new Date(timeCreated);
    prependTrollboxMessage(createdBy, date.toISOString(), content, isUserAdmin, fadeIn);
}

function prependTrollboxMessage(author, timeCreated, text, isAdmin, fadeIn) {
  var messageBlock = null
  if(author) {
    if(isAdmin) {
        messageBlock = $('<div class="alert alert-dismissable alert-success" data-toggle="tooltip" data-placement="top" title="Published at: ' + timeCreated + '">')
    } else {
        messageBlock = $('<div class="alert alert-dismissable" data-toggle="tooltip" data-placement="top" title="Published at: ' + timeCreated + '">')
    }
    messageBlock.append('<span class="glyphicon glyphicon-user"></span>&nbsp;')
    messageBlock.append('<strong>' + author + ':</strong> &nbsp;')
  } else {
    messageBlock = $('<div class="alert alert-dismissable alert-info" data-toggle="tooltip" data-placement="top" title="Published at: ' + timeCreated + '">')
    messageBlock.append('<span class="glyphicon glyphicon-king"></span>&nbsp;')
    messageBlock.append('<strong>Ada:</strong> &nbsp;')
  }
  messageBlock.append(text)
  if(fadeIn) {
      messageBlock.hide();
  }
  $('#trollmessagebox').append(messageBlock);
  if(fadeIn) {
      messageBlock.fadeIn('2000');
  }
}

function showMessage(text) {
  $('#messageDiv').hide();

  var closeX = '<button type="button" class="close" data-dismiss="alert">×</button>'
  var messageBlock = $('<div class="alert alert-dismissable alert-success">')
  messageBlock.append(closeX)
  messageBlock.append('<strong>Well done!</strong> ')
  messageBlock.append(text)
  $('#messageDiv').html(messageBlock);
  $('#messageDiv').fadeIn('2000');

  addMessageDividerIfNeeded();
  registerMessageDividerRemoval();
}

function showErrorResponse(data) {
    if (data.responseText) {
        showError(data.responseText)
    } else {
        if (data.status == 401)
            showError("Access denied! We're sorry, but you are not authorized to perform the requested operation.")
        else
            showError(data.status + ": " + data.statusText)
    }
}

function showError(message) {
  $('#errorDiv').hide();

  var closeX = '<button type="button" class="close" data-dismiss="alert">×</button>'
  var innerDiv = $('<div class="alert alert-dismissable alert-danger">');
  innerDiv.append(closeX);
  innerDiv.append(message);
  $('#errorDiv').html(innerDiv);
  $('#errorDiv').fadeIn('2000');
  addMessageDividerIfNeeded();
  registerMessageDividerRemoval();
}

function hideMessages() {
  $('#messageDiv').fadeOut('2000');
  $('#messageDiv').html('');
}

function showErrors(errors) {
  if (errors.length > 0) {
    var closeX = '<button type="button" class="close" data-dismiss="alert">×</button>'
    $('#errorDiv').hide();
    $('#errorDiv').html("");
    $.each(errors, function(index, error) {
      var innerDiv = $('<div class="alert alert-dismissable alert-danger">');
      innerDiv.append(closeX);
      innerDiv.append(error.message);
      $('#errorDiv').append(innerDiv);
    });
    $('#errorDiv').fadeIn('2000');
    addMessageDividerIfNeeded();
    registerMessageDividerRemoval();
  }
}

function addMessageDividerIfNeeded() {
    var messagesCount = $('#messageContainer').find('.alert-dismissable').length
    if ($('#messageContainer .messageDivider').length == 0 && messagesCount > 0) {
        $('#messageContainer').append('<hr class="messageDivider"/>')
    }
}

function registerMessageDividerRemoval() {
    $('#messageContainer .alert-dismissable .close').click(function () {
        var messagesCount = $('#messageContainer').find('.alert-dismissable').length
        if (messagesCount == 1) {
            $('#messageContainer .messageDivider').remove();
        }
    });
}

function hideErrors() {
  $('#errorDiv').fadeOut('2000');
  $('#errorDiv').html('');
}

function getCheckedTableIds(tableId, objectIdName) {
  var ids = []
  $('#' + tableId + ' tbody tr').each(function() {
    var id = getRowId($(this), objectIdName)
    var checked = $(this).find("td input.table-selection[type=checkbox]").is(':checked');
    if (checked) {
      ids.push(id)
    }
  });
  return ids
};

function getNearestRowId(element, objectIdName) {
  var row = element.closest('tr');
  return getRowId(row, objectIdName)
}

function getRowId(rowElement, objectIdName) {
  var idElement = rowElement.find('#' + objectIdName)
  var id = idElement.text().trim();
  if (!id) {
    id = idElement.val().trim();
  }
  return id
}

function handleModalButtonEnterPressed(modalName, submitButtonName, action, hideOnEnter) {
  $("#" + modalName).keypress(function (e) {
    if (e.keyCode == 13) {
      e.preventDefault();
      action()
      if(hideOnEnter) {
          $("#" + modalName).modal("hide")
      }
    }
  });

  $("#" + submitButtonName).click(action);
}

function shorten(string, length) {
    return (string.length > length) ?
        string.substring(0, length) + ".."
    :
        string
}

function loadNewContent(url, elementId, data, callType) {
    $.ajax({
        url: url,
        data: data,
        type: (callType) ? callType : "GET",
        success: function (html) {
            $("#" + elementId).html(html);
        },
        error: function(data) {
            showErrorResponse(data)
        }
    });
}

function loadNewTableContent(element, url, data, callType) {
    $.ajax({
        url: url,
        data: data,
        type: (callType) ? callType : "GET",
        success: function(content) {
            var tableDiv = element.closest(".table-div")
            $(tableDiv).html(content);
        },
        error: showErrorResponse
    });
}

function activateRowClickable() {
    $(function() {
        $(".clickable-row").dblclick(function () {
            window.document.location = $(this).data("href");
        });
        $(".no-rowClicked").dblclick(function (event) {
            event.stopPropagation();
        });
    });
}

function getModalValues(modalElementId) {
    var values = {};
    $('#' + modalElementId +' input, #' + modalElementId +' select, #' + modalElementId +' textarea').each(function () {
        if (this.id) {
            if ($(this).attr('type') != "checkbox") {
                values[this.id] = $(this).val()
            } else {
                values[this.id] = $(this).is(':checked')
            }
        }
    })
    return values;
}

function showMLOutput(evalRates) {
    $("#outputDiv").html("");

    var header = ["Metrics", "Training", "Test"]
    var showReplicationRates = evalRates[0].replicationEvalRate != null
    if (showReplicationRates)
        header = header.concat("Replication")

    function float3(value) {
        return (value) ? value.toFixed(3) : ""
    }

    var rowData = evalRates.map(function(item) {
        var data = [item.metricName, float3(item.trainEvalRate), float3(item.testEvalRate)]
        if (showReplicationRates)
            data = data.concat(float3(item.replicationEvalRate))
        return data
    });

    var table = createTable(header, rowData);
    $("#outputDiv").html(table);
    $('#outputDiv').fadeIn('2000');
}

function getCookie(name) {
    match = document.cookie.match(new RegExp(name + '=([^;]+)'));
    if (match) return match[1];
}

function flatten(data) {
    var result = {};
    function recurse (cur, prop) {
        if (Object(cur) !== cur) {
            result[prop] = cur;
        } else if (Array.isArray(cur)) {
            for(var i=0, l=cur.length; i<l; i++)
                recurse(cur[i], prop + "[" + i + "]");
            if (l == 0)
                result[prop] = [];
        } else {
            var isEmpty = true;
            for (var p in cur) {
                isEmpty = false;
                recurse(cur[p], prop ? prop+"."+p : p);
            }
            if (isEmpty && prop)
                result[prop] = {};
        }
    }
    recurse(data, "");
    return result;
}

function removeDuplicates(array) {
    return array.reduce(function(a,b){
        if (a.indexOf(b) < 0 ) a.push(b);
        return a;
    },[]);
}

function getRowValue(row, elementId) {
    var element = row.find('#' + elementId);
    var value = null;
    if (element.length > 0) {
        value = element.val().trim()
        if (!value)
            value = null
    }
    return value;
}

function createTable(columnNames, rows) {
    var table = $("<table class='table table-striped'>")

    // head
    var thead = $("<thead>")
    var theadTr = $("<tr>")

    $.each(columnNames, function(index, columnName) {
        var th = "<th class='col header'>" + columnName + "</th>"
        theadTr.append(th)
    })
    thead.append(theadTr)
    table.append(thead)

    // body
    var tbody = $("<tbody>")

    $.each(rows, function(index, row) {
        var tr = $("<tr>")
        $.each(row, function(index, item) {
            var td = "<td>" + item + "</td>"
            tr.append(td)
        })
        tbody.append(tr)
    })
    table.append(tbody)

    return table
}

function initJsTree(treeElementId, data, typesSetting) {
    $('#' + treeElementId).jstree({
        "core" : {
            "animation" : 0,
            "check_callback" : true,
            "themes" : {
                'responsive' : false,
                'variant' : 'large',
                "stripes" : true
            },
            'data' : data
        },
        "types" : typesSetting,
        "search": {
        "case_insensitive": true,
            "show_only_matches" : true,
            "search_leaves_only": true
        },

        "plugins" : [
            "search", "sort", "state", "types", "wholerow" // "contextmenu", "dnd",
        ]
    });

    $('#' + treeElementId).jstree("deselect_all");
}

function moveModalRight(modalId) {
    $('#' + modalId).one('hidden.bs.modal', function () {
//            $(this).data('bs.modal', null);
        var modalDialog = $('#' + modalId + ' .modal-dialog:first')
        var isRight = modalDialog.hasClass("modal-right")
        var isLeft = modalDialog.hasClass("modal-left")

        if (isRight) {
            modalDialog.removeClass("modal-right")
            modalDialog.addClass("modal-left")
        } else if (isLeft) {
            modalDialog.removeClass("modal-left")
        } else {
            modalDialog.addClass("modal-right")
        }

        $('#' + modalId).modal('show');
    });
}

function addSpinner(element, style) {
    if (style)
        element.append("<div class='spinner' style='margin: auto; " + style + "'></div>")
    else
        element.append("<div class='spinner' style='margin: auto;'></div>")
}

function updateFilterValueElement(filterElement, data) {
    var fieldType = (data.isArray) ? data.fieldType + " Array" : data.fieldType
    filterElement.find("#fieldInfo").html("Field type: " + fieldType)
    var conditionTypeElement = filterElement.find("#conditionType")

    var conditionType = conditionTypeElement.val();
    var isInNinType = (conditionType == "in" || conditionType == "nin") ? "multiple" : ""

    var newValueElement = null;
    if (data.allowedValues.length > 0) {
        conditionTypeElement.change(function() {
            var valueElement = $(this).parent().find("#value")

            if (this.value == "in" || this.value == "nin") {
                valueElement.prop('multiple', 'multiple');
                var emptyOption = valueElement.find('option[value=""]');
                emptyOption.remove()
            } else {
                valueElement.removeProp('multiple');
                if (valueElement.find('option[value=""]').length == 0) {
                    var firstOption = valueElement.find('option').first();
                    firstOption.before('<option value="">[undefined]</option>');
                }
            }

            valueElement.selectpicker('destroy');
            valueElement.selectpicker();
        })

        var multiple = (isInNinType) ? "multiple" : ""
        newValueElement = $("<select id='value' " + multiple + " class='selectpicker float-left show-menu-arrow form-control conditionValue'>")
        if (!isInNinType)
            newValueElement.append("<option value=''>[undefined]</option>")
        $.each(data.allowedValues, function (index, keyValue) {
            newValueElement.append("<option value='" + keyValue[0] + "'>" + keyValue[1] + "</option>")
        });
    } else {
        newValueElement = $("<input id='value' class='float-left conditionValue' placeholder='Condition'/>")
    }
    var oldValueElement = filterElement.find("#addEditConditionModal .conditionValue")
    var oldValue = oldValueElement.val()
    oldValueElement.selectpicker('destroy');
    oldValueElement.replaceWith(newValueElement);

    if (data.allowedValues.length > 0) {
        if (isInNinType) {
            oldValue = oldValue.split(",");
        }
        newValueElement.val(oldValue);
        newValueElement.selectpicker();
    } else {
        newValueElement.val(oldValue);
    }
}

function updatePlusMinusIcon(element) {
    var iconPlus = element.find("span.glyphicon-plus:first");
    var iconMinus = element.find("span.glyphicon-minus:first");
    if (iconPlus.length) {
        iconPlus.removeClass("glyphicon-plus");
        iconPlus.addClass("glyphicon-minus");
    } else {
        iconMinus.removeClass("glyphicon-minus");
        iconMinus.addClass("glyphicon-plus");
    }
}

function scrollToAnchor(id, offset){
    var tag = $("#" + id)
    if (!offset)
        offset = 0
    $('html,body').animate({scrollTop: tag.offset().top + offset},'slow');
}

function createIndependenceTestTable(results, withTestType) {
    var header = ["Field", "p-Value", "Degree of Freedom", "Stats/F-Value"]
    if (withTestType)
        header.push("Test Type")

    var rowData = results.map(function(item) {
        var fieldLabel = item[0]
        var result = item[1]

        var color =
            (result.pValue >= 0.05) ? "black":
                (result.pValue >= 0.01) ? "forestGreen":
                    (result.pValue >= 0.001) ? "green" : "darkGreen"

        var significantStyleDivStart = (result.pValue < 0.05) ? ("<div style='color: " + color + "; font-weight: bold'>") : "<div>"
        var isAnovaTest = (result.FValue != null)
        var rowStart = [fieldLabel,  significantStyleDivStart + result.pValue.toExponential(3).replace("e", " E") + "</div>"]

        var rowEnd = (isAnovaTest) ?
            [result.dfbg, result.FValue.toFixed(2)]
            :
            [result.degreeOfFreedom, result.statistics.toFixed(2)]

        var testTypeText = (isAnovaTest) ? "ANOVA" : "Chi-Square"

        if (withTestType)
            rowEnd.push(testTypeText)

        return rowStart.concat(rowEnd)
    });

    return createTable(header, rowData);
}

function msToStandardDateString(ms) {
    var date = new Date(ms)
    return date.getFullYear() + '-' +('0' + (date.getMonth()+1)).slice(-2)+ '-' + date.getDate() + ' ' + date.getHours() + ':'+('0' + (date.getMinutes())).slice(-2)+ ':' + date.getSeconds();
}

function addFilterModelBeforeModalSubmit(modalId, filterElement, filterParamName) {
    $('#' + modalId + ' form').submit(function(event) {
        event.preventDefault();

        // remove the old filter
        $(this).find("input[name='" + filterParamName + "']").remove();

        // add a new one
        var filterModel = $(filterElement).multiFilter("getModel")

        var params = {}
        params[filterParamName] = JSON.stringify(filterModel)
        addParams($(this), params)

        // submit
        this.submit();
    });
}

function submitModalOnEnter(event, element) {
    if (event.which == 13) {
        var modalFooter = $(element).closest(".modal-body").parent().find(".modal-footer")
        modalFooter.find("#submitButton").trigger("click");
        return false;
    }
}