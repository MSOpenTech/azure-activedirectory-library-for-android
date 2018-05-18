package com.microsoft.identity.common.test.automation.questions;

import com.microsoft.identity.common.internal.net.ObjectMapper;
import com.microsoft.identity.common.test.automation.actors.User;
import com.microsoft.identity.common.test.automation.model.ReadCacheResult;
import com.microsoft.identity.common.test.automation.model.ResultsMapper;
import com.microsoft.identity.common.test.automation.ui.Results;

import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;
import net.serenitybdd.screenplay.questions.Text;

public class TokenCacheItemCount implements Question<Integer> {

    @Override
    public Integer answeredBy(Actor actor) {
        String results = Text.of(Results.RESULT_FIELD).viewedBy(actor).asString();
        ReadCacheResult readCacheResult = ResultsMapper.GetReadCacheResultFromString(results);
        User user = (User) actor;
        user.setCacheResult(readCacheResult.tokenCacheItems.get(0));
        return readCacheResult.itemCount;
    }

    public static Question<Integer> displayed(){
        return new TokenCacheItemCount();
    }
}