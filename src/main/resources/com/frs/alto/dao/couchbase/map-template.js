function(doc, meta) {
	var keyTokens = meta.id.split("#");
	if (keyTokens.length == 3) {
		if (keyTokens[1] == '$keyNameSpace') {
			emit([keyTokens[0], keyTokens[2]], meta.id);
		}
	}
	else {
		if (keyTokens[0] == '$keyNameSpace') {
			emit(keyTokens[1], meta.id);
		}
	}
}