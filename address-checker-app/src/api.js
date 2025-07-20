/*
 * Copyright 2025 Samitha Chathuranga
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import axios from 'axios';

const NZ_POST_URL = process.env.REACT_APP_ADDRESS_CHECKER_API_BASE_URL + "suggestions";

export const fetchAddressSuggestions = async (query) => {
  console.log("Url:" + NZ_POST_URL);  
  console.log("Query passed:" + query);  
  if (!query) return [];

  try {
    const response = await axios.get(NZ_POST_URL, {
      params: {
        q: query,
        max: 6
      },
      headers: {
        'Accept': 'application/json'
      }
    });
    console.log("Raw response from API:", response); // <-- log here
    return response || [];
  } catch (error) {
    console.error('NZ Post API error:', error);
    return [];
  }
};
